"""Training and inference for IntelliDesk.

Three jobs, deliberately kept separate:

1. Category classification  - TF-IDF + logistic regression, with calibrated-ish
   probabilities used as a confidence signal. The backend refuses to auto-route
   anything below its confidence threshold.
2. Similarity search        - TF-IDF + cosine over past resolved incidents. The
   nearest neighbours' resolution text is what the agent actually sees, so the
   suggestion is always attributable to a real past ticket.
3. Resolution time          - gradient boosting on category, priority, impact,
   urgency. Reported against a baseline (predict the category median) because a
   regression that cannot beat the median is not worth shipping.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, mean_absolute_error
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import OrdinalEncoder

HERE = Path(__file__).parent
DATA = HERE / "data"
ARTEFACTS = HERE / "artefacts"
CORPUS_PATH = DATA / "corpus.csv"

MODEL_VERSION = "m2-tfidf-1"
FEATURES = ["category", "priority", "impact", "urgency"]


@dataclass
class Bundle:
    """Everything inference needs, loaded once at start-up."""

    vectoriser: TfidfVectorizer
    classifier: LogisticRegression
    corpus: pd.DataFrame
    corpus_matrix: object
    regressor: HistGradientBoostingRegressor
    encoder: OrdinalEncoder
    category_medians: dict
    metrics: dict

    def analyse(self, text: str, impact: str, urgency: str, top_k: int = 3) -> dict:
        vector = self.vectoriser.transform([text])

        probabilities = self.classifier.predict_proba(vector)[0]
        best = int(np.argmax(probabilities))
        category = str(self.classifier.classes_[best])
        confidence = float(probabilities[best])

        similar = self._similar(vector, top_k)
        subcategory = similar[0]["subcategory"] if similar else "General"
        priority = _priority(impact, urgency)

        hours = self._predict_hours(category, priority, impact, urgency)

        return {
            "category": category,
            "subcategory": subcategory,
            "confidence": confidence,
            "predicted_resolution_hours": hours,
            "sla_breach_risk": _breach_risk(hours, priority),
            "similar_incidents": [
                {
                    "reference": row["reference"],
                    "title": row["title"],
                    "resolution": row["resolution"],
                    "similarity": row["similarity"],
                }
                for row in similar
            ],
            "suggested_steps": _steps(similar),
            "model_version": MODEL_VERSION,
        }

    def _similar(self, vector, top_k: int) -> list[dict]:
        # Both matrices are L2-normalised by TfidfVectorizer, so the dot product
        # is already the cosine similarity.
        scores = (self.corpus_matrix @ vector.T).toarray().ravel()
        if scores.size == 0:
            return []
        top = np.argsort(scores)[::-1][:top_k]
        results = []
        for index in top:
            score = float(scores[index])
            if score < 0.10:  # below this the "match" is noise
                continue
            row = self.corpus.iloc[int(index)]
            results.append({
                "reference": str(row["reference"]),
                "title": str(row["title"]),
                "resolution": str(row["resolution"]),
                "subcategory": str(row["subcategory"]),
                "similarity": round(score, 3),
            })
        return results

    def _predict_hours(self, category: str, priority: str, impact: str, urgency: str) -> Optional[float]:
        try:
            frame = pd.DataFrame([[category, priority, impact, urgency]], columns=FEATURES)
            encoded = self.encoder.transform(frame)
            return round(float(self.regressor.predict(encoded)[0]), 1)
        except Exception:
            # Unseen category, or the model failed to load. Fall back to the median
            # rather than returning nothing.
            return self.category_medians.get(category)


def _priority(impact: str, urgency: str) -> str:
    table = {
        ("HIGH", "HIGH"): "P1", ("HIGH", "MEDIUM"): "P2", ("HIGH", "LOW"): "P3",
        ("MEDIUM", "HIGH"): "P2", ("MEDIUM", "MEDIUM"): "P3", ("MEDIUM", "LOW"): "P4",
        ("LOW", "HIGH"): "P3", ("LOW", "MEDIUM"): "P4", ("LOW", "LOW"): "P4",
    }
    return table.get((impact.upper(), urgency.upper()), "P3")


SLA_TARGET_HOURS = {"P1": 4, "P2": 8, "P3": 24, "P4": 72}


def _breach_risk(predicted_hours: Optional[float], priority: str) -> Optional[float]:
    """How likely this incident is to miss its target, as a smooth function of how
    close the prediction sits to the SLA target. Not a trained classifier — and it
    is not presented as one."""
    if predicted_hours is None:
        return None
    target = SLA_TARGET_HOURS.get(priority, 24)
    ratio = predicted_hours / target
    risk = 1 / (1 + np.exp(-6 * (ratio - 0.75)))
    return round(float(risk), 2)


def _steps(similar: list[dict]) -> list[str]:
    """Deduplicated resolution text from the nearest past incidents.

    Nothing is generated. Every line an agent sees came from a ticket that was
    actually resolved that way, which is what makes the suggestion defensible.
    """
    steps: list[str] = []
    for row in similar:
        text = row["resolution"].strip()
        if text and text not in steps:
            steps.append(text)
    return steps


def train(corpus_path: Path = CORPUS_PATH) -> Bundle:
    frame = pd.read_csv(corpus_path)
    frame["text"] = frame["title"].fillna("") + ". " + frame["description"].fillna("")

    train_frame, test_frame = train_test_split(
        frame, test_size=0.25, random_state=42, stratify=frame["category"])

    vectoriser = TfidfVectorizer(
        ngram_range=(1, 2), min_df=2, sublinear_tf=True, stop_words="english")
    x_train = vectoriser.fit_transform(train_frame["text"])
    x_test = vectoriser.transform(test_frame["text"])

    classifier = LogisticRegression(max_iter=1000, C=4.0, class_weight="balanced")
    classifier.fit(x_train, train_frame["category"])

    predictions = classifier.predict(x_test)
    accuracy = float(accuracy_score(test_frame["category"], predictions))
    macro_f1 = float(f1_score(test_frame["category"], predictions, average="macro"))
    labels = sorted(frame["category"].unique())
    per_category = {
        label: round(float(score), 3)
        for label, score in zip(
            labels, f1_score(test_frame["category"], predictions, average=None, labels=labels))
    }
    confused = _confusions(test_frame["category"], predictions, labels)

    # Resolution-time model.
    encoder = OrdinalEncoder(handle_unknown="use_encoded_value", unknown_value=-1)
    y_train = train_frame["resolution_hours"].to_numpy()
    y_test = test_frame["resolution_hours"].to_numpy()
    encoder.fit(frame[FEATURES])
    regressor = HistGradientBoostingRegressor(max_iter=250, learning_rate=0.08, random_state=42)
    regressor.fit(encoder.transform(train_frame[FEATURES]), y_train)

    mae = float(mean_absolute_error(y_test, regressor.predict(encoder.transform(test_frame[FEATURES]))))

    # Baseline: predict the training median for the incident's category.
    medians = train_frame.groupby("category")["resolution_hours"].median().to_dict()
    overall_median = float(train_frame["resolution_hours"].median())
    baseline = np.array([medians.get(c, overall_median) for c in test_frame["category"]])
    baseline_mae = float(mean_absolute_error(y_test, baseline))

    # The similarity corpus is only ever the resolved incidents we trained on.
    corpus = train_frame.reset_index(drop=True)
    corpus_matrix = vectoriser.transform(corpus["text"])

    metrics = {
        "model_version": MODEL_VERSION,
        "trained_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "training_rows": int(len(train_frame)),
        "categories": labels,
        "holdout_accuracy": round(accuracy, 4),
        "holdout_macro_f1": round(macro_f1, 4),
        "per_category_f1": per_category,
        "most_confused_pairs": confused,
        "resolution_model_mae_hours": round(mae, 2),
        "baseline_mae_hours": round(baseline_mae, 2),
        "corpus_is_synthetic": True,
    }

    return Bundle(vectoriser, classifier, corpus, corpus_matrix,
                  regressor, encoder, {k: round(float(v), 1) for k, v in medians.items()}, metrics)


def _confusions(truth, predicted, labels) -> list[dict]:
    """The pairs the classifier mixes up most. Reported openly: knowing that Network
    and Connectivity blur into each other is more useful than a single accuracy number."""
    matrix = confusion_matrix(truth, predicted, labels=labels)
    pairs = []
    for i, actual in enumerate(labels):
        for j, guess in enumerate(labels):
            if i != j and matrix[i][j] > 0:
                pairs.append({"actual": actual, "predicted": guess, "count": int(matrix[i][j])})
    pairs.sort(key=lambda p: p["count"], reverse=True)
    return pairs[:5]


def save(bundle: Bundle) -> None:
    ARTEFACTS.mkdir(exist_ok=True)
    joblib.dump(bundle, ARTEFACTS / "bundle.joblib")
    (ARTEFACTS / "metrics.json").write_text(json.dumps(bundle.metrics, indent=2))


def load() -> Optional[Bundle]:
    path = ARTEFACTS / "bundle.joblib"
    if not path.exists():
        return None
    return joblib.load(path)


if __name__ == "__main__":
    # Deliberately not training here. Running this file directly would pickle Bundle
    # as `__main__.Bundle`, and the API process — which imports it as `model` — could
    # not unpickle it. Training lives in train.py so the class path is always `model`.
    raise SystemExit("Run training with: python train.py")
