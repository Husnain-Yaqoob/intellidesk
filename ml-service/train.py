"""Trains the models and writes them to artefacts/.

    python train.py

Generates the synthetic corpus first if there isn't one. Safe to re-run: the API
picks up a new model on restart, or immediately via POST /reload.
"""

from __future__ import annotations

import json

import generate_corpus
import model


def main() -> None:
    if not model.CORPUS_PATH.exists():
        print("No corpus found — generating a synthetic one.")
        generate_corpus.main()

    bundle = model.train()
    model.save(bundle)

    metrics = bundle.metrics
    print(json.dumps(metrics, indent=2))

    improvement = metrics["baseline_mae_hours"] - metrics["resolution_model_mae_hours"]
    if improvement <= 0:
        print("\nWARNING: the resolution-time model does not beat predicting the "
              "category median. Do not present it as a working model.")
    else:
        print(f"\nResolution-time model beats the median baseline by "
              f"{improvement:.2f} hours MAE.")


if __name__ == "__main__":
    main()
