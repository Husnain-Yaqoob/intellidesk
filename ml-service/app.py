"""FastAPI service the Java backend calls at triage time."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

import llm
import model
from agent import DraftAgent
from schemas import AnalyseRequest, AnalyseResponse, DraftRequest, DraftResponse, ModelInfo

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("intellidesk.ml")

state: dict = {"bundle": None, "llm": None}


@asynccontextmanager
async def lifespan(app: FastAPI):
    bundle = model.load()
    if bundle is None:
        log.warning("No trained model found. Run: python generate_corpus.py && python model.py")
    else:
        log.info("Loaded model %s trained on %d incidents",
                 bundle.metrics["model_version"], bundle.metrics["training_rows"])
    state["bundle"] = bundle
    # Drafting is optional. With LLM_ENABLED=false (or no model server running) the
    # service does everything it did before and /draft answers 503.
    state["llm"] = llm.from_env()
    if state["llm"] is not None:
        log.info("LLM drafting enabled with model %s", state["llm"].name)
    yield
    state["bundle"] = None
    state["llm"] = None


app = FastAPI(title="IntelliDesk ML service", version="1.0.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model_loaded": state["bundle"] is not None,
        "llm_enabled": state["llm"] is not None,
        "llm_model": state["llm"].name if state["llm"] is not None else None,
    }


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


@app.post("/draft", response_model=DraftResponse)
def draft(request: DraftRequest) -> DraftResponse:
    """Drafts first steps for an incident, grounded in past resolutions.

    Slow by the standards of /analyse — seconds, not milliseconds, on a local model —
    which is why the backend only calls it when an agent asks, never while an
    incident is being created.
    """
    bundle = state["bundle"]
    if bundle is None:
        raise HTTPException(status_code=503, detail="No trained model loaded")
    if state["llm"] is None:
        raise HTTPException(status_code=503, detail="LLM drafting is disabled")
    try:
        result = DraftAgent(bundle, state["llm"]).draft(
            request.title, request.description, request.category)
    except llm.LlmUnavailable as error:
        log.warning("LLM unavailable: %s", error)
        raise HTTPException(status_code=503, detail="Language model unavailable") from error
    return DraftResponse(**result)


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
