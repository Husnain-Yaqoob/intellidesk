"""FastAPI service the Java backend calls at triage time."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

import model
from schemas import AnalyseRequest, AnalyseResponse, ModelInfo

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("intellidesk.ml")

state: dict = {"bundle": None}


@asynccontextmanager
async def lifespan(app: FastAPI):
    bundle = model.load()
    if bundle is None:
        log.warning("No trained model found. Run: python generate_corpus.py && python model.py")
    else:
        log.info("Loaded model %s trained on %d incidents",
                 bundle.metrics["model_version"], bundle.metrics["training_rows"])
    state["bundle"] = bundle
    yield
    state["bundle"] = None


app = FastAPI(title="IntelliDesk ML service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model_loaded": state["bundle"] is not None}


@app.post("/analyse", response_model=AnalyseResponse)
def analyse(request: AnalyseRequest) -> AnalyseResponse:
    bundle = state["bundle"]
    if bundle is None:
        # 503, not 500: the service is fine, it just has nothing to serve yet. The
        # backend treats this the same as unreachable and queues the incident for
        # manual triage.
        raise HTTPException(status_code=503, detail="No trained model loaded")

    result = bundle.analyse(request.text(), request.impact, request.urgency)
    return AnalyseResponse(**result)


@app.get("/model-info", response_model=ModelInfo)
def model_info() -> ModelInfo:
    bundle = state["bundle"]
    if bundle is None:
        raise HTTPException(status_code=503, detail="No trained model loaded")
    return ModelInfo(**bundle.metrics)


@app.post("/reload")
def reload_model() -> dict:
    """Picks up a newly trained model without a restart."""
    state["bundle"] = model.load()
    return {"model_loaded": state["bundle"] is not None}
