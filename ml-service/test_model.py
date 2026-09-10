"""Tests for the ML service.

These check behaviour that would embarrass the app in a demo — a classifier that
cannot beat chance, a similarity search that returns unrelated tickets, a regression
that loses to the median — rather than asserting exact numbers that drift on retrain.
"""

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import generate_corpus
import model


@pytest.fixture(scope="module")
def bundle(tmp_path_factory):
    corpus_path = tmp_path_factory.mktemp("data") / "corpus.csv"
    rows = generate_corpus.generate(rows=900, seed=11)

    import csv
    with corpus_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    return model.train(Path(corpus_path))


def test_classifier_is_clearly_better_than_guessing(bundle):
    categories = len(bundle.metrics["categories"])
    chance = 1 / categories
    assert bundle.metrics["holdout_accuracy"] > max(0.7, chance * 3)


def test_every_category_is_learned_not_just_the_common_ones(bundle):
    for category, score in bundle.metrics["per_category_f1"].items():
        assert score > 0.5, f"{category} is barely being learned (F1 {score})"


def test_resolution_model_beats_the_category_median(bundle):
    assert bundle.metrics["resolution_model_mae_hours"] < bundle.metrics["baseline_mae_hours"], (
        "The regression does not beat predicting the category median, "
        "so it should not be shipped"
    )


def test_similar_incidents_are_actually_similar(bundle):
    result = bundle.analyse(
        "VPN disconnects every ten minutes when I work from home", "HIGH", "HIGH")

    assert result["category"] == "Network"
    assert result["similar_incidents"], "expected at least one neighbour"
    assert all(0 <= s["similarity"] <= 1 for s in result["similar_incidents"])
    assert result["similar_incidents"][0]["similarity"] > 0.3
    assert any("vpn" in s["title"].lower() for s in result["similar_incidents"])


def test_suggested_steps_come_from_real_resolutions(bundle):
    result = bundle.analyse("Outlook crashes when I open a PDF attachment", "MEDIUM", "HIGH")

    assert result["suggested_steps"]
    resolutions = {s["resolution"] for s in result["similar_incidents"]}
    for step in result["suggested_steps"]:
        assert step in resolutions, "a suggested step was not traceable to a past incident"


def test_nonsense_input_produces_low_confidence(bundle):
    result = bundle.analyse("zxcvbnm qwerty asdfgh", "LOW", "LOW")
    # It must still answer, but it should not be confident about it.
    assert result["confidence"] < 0.75


def test_higher_priority_predicts_faster_resolution(bundle):
    critical = bundle.analyse("Laptop will not power on", "HIGH", "HIGH")
    low = bundle.analyse("Laptop will not power on", "LOW", "LOW")
    assert critical["predicted_resolution_hours"] < low["predicted_resolution_hours"]


def test_sla_risk_rises_with_predicted_time(bundle):
    low = model._breach_risk(1.0, "P3")
    high = model._breach_risk(40.0, "P3")
    assert 0 <= low < high <= 1


def test_api_returns_503_before_a_model_is_trained(monkeypatch):
    import app as app_module

    monkeypatch.setattr(app_module.model, "load", lambda: None)
    with TestClient(app_module.app) as client:
        assert client.get("/health").json()["model_loaded"] is False
        response = client.post("/analyse", json={"title": "x", "description": "y"})
        assert response.status_code == 503


def test_api_round_trip(monkeypatch, bundle):
    import app as app_module

    monkeypatch.setattr(app_module.model, "load", lambda: bundle)
    with TestClient(app_module.app) as client:
        response = client.post("/analyse", json={
            "title": "Cannot connect to VPN from home",
            "description": "The VPN client says authentication failed.",
            "impact": "HIGH",
            "urgency": "HIGH",
        })
        assert response.status_code == 200
        body = response.json()
        assert body["category"] == "Network"
        assert 0 <= body["confidence"] <= 1
        assert body["model_version"]

        info = client.get("/model-info").json()
        assert info["corpus_is_synthetic"] is True
        assert info["training_rows"] > 0
