"""Wire contract with the Java backend.

These shapes mirror MlContract.java exactly. If you change one, change both.
"""

from typing import List, Optional

from pydantic import BaseModel, Field


class AnalyseRequest(BaseModel):
    title: str
    description: str
    impact: str = "MEDIUM"
    urgency: str = "MEDIUM"

    def text(self) -> str:
        """Title and description are both signal; the title is often the clearer one."""
        return f"{self.title}. {self.description}".strip()


class SimilarIncident(BaseModel):
    reference: str
    title: str
    resolution: str
    similarity: float


class AnalyseResponse(BaseModel):
    category: str
    subcategory: str
    confidence: float = Field(ge=0.0, le=1.0)
    predicted_resolution_hours: Optional[float] = None
    sla_breach_risk: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    similar_incidents: List[SimilarIncident] = []
    suggested_steps: List[str] = []
    model_version: str


class ModelInfo(BaseModel):
    """Served at /model-info and rendered in the app, so the numbers are visible
    rather than buried in a notebook nobody opens."""

    model_version: str
    trained_at: str
    training_rows: int
    categories: List[str]
    holdout_accuracy: float
    holdout_macro_f1: float
    per_category_f1: dict
    most_confused_pairs: List[dict]
    resolution_model_mae_hours: float
    baseline_mae_hours: float
    corpus_is_synthetic: bool


# ---------------------------------------------------------------------------
# LLM drafting. Mirrors MlContract.DraftRequest / DraftResponse.
# ---------------------------------------------------------------------------

class DraftRequest(BaseModel):
    title: str = Field(max_length=200)
    description: str = Field(max_length=5000)
    category: Optional[str] = None


class DraftStep(BaseModel):
    action: str
    source: str
    source_title: str


class DraftSource(BaseModel):
    reference: str
    title: str
    resolution: str
    similarity: float


class DraftGuards(BaseModel):
    injection_suspected: bool = False
    injection_signals: List[str] = []
    redactions: int = 0
    rejected_steps: int = 0
    rewritten_steps: int = 0
    links_removed: int = 0
    reply_withheld: bool = False


class DraftToolCall(BaseModel):
    name: str
    query: str
    results: int


class DraftTrace(BaseModel):
    model: str
    rounds: int
    tool_calls: List[DraftToolCall] = []
    prompt_tokens: int = 0
    completion_tokens: int = 0
    latency_ms: int = 0


class DraftResponse(BaseModel):
    status: str                      # "drafted" | "abstained"
    summary: str = ""
    steps: List[DraftStep] = []
    reply_to_user: str = ""
    confidence: str = "low"
    abstain_reason: str = ""
    sources: List[DraftSource] = []
    guards: DraftGuards
    trace: DraftTrace
    draft_version: str
