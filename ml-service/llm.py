"""The one place that talks to a language model.

Everything goes through the OpenAI-compatible Chat Completions API, because that is
the closest thing the industry has to a common wire format. The default target is a
local Ollama server — free, offline, and the ticket text never leaves the machine —
but the same client works unchanged against OpenAI, Groq, Azure OpenAI or a vLLM box
by changing three environment variables:

    LLM_BASE_URL   default http://localhost:11434/v1   (Ollama)
    LLM_MODEL      default qwen2.5:3b                   (small, good at tool calls)
    LLM_API_KEY    unset for Ollama

The rest of the service only sees the `ChatModel` protocol, so the tests drive the
agent with a scripted fake and never need a network, a GPU or a key.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from typing import Optional, Protocol

import httpx


@dataclass(frozen=True)
class LlmConfig:
    enabled: bool
    base_url: str
    model: str
    api_key: Optional[str]
    timeout_s: float
    # Low on purpose. This is drafting resolution steps from evidence, not creative
    # writing — the same ticket should get the same draft.
    temperature: float = 0.1
    max_tokens: int = 700

    @classmethod
    def from_env(cls) -> "LlmConfig":
        return cls(
            enabled=os.getenv("LLM_ENABLED", "true").strip().lower() in ("1", "true", "yes"),
            base_url=os.getenv("LLM_BASE_URL", "http://localhost:11434/v1").rstrip("/"),
            model=os.getenv("LLM_MODEL", "qwen2.5:3b"),
            api_key=os.getenv("LLM_API_KEY") or None,
            timeout_s=float(os.getenv("LLM_TIMEOUT_S", "90")),
        )


class LlmUnavailable(Exception):
    """The model could not be reached or returned something unusable.

    Raised rather than swallowed: the endpoint turns it into a 503, and the Java side
    already treats a 503 from this service as "carry on without it".
    """


@dataclass
class ToolCall:
    id: str
    name: str
    arguments: str          # raw JSON text, exactly as the model produced it


@dataclass
class ChatResult:
    content: Optional[str]
    tool_calls: list[ToolCall] = field(default_factory=list)
    prompt_tokens: int = 0
    completion_tokens: int = 0


class ChatModel(Protocol):
    name: str

    def chat(self, messages: list[dict], tools: list[dict]) -> ChatResult: ...


class OpenAICompatibleModel:
    """Chat Completions over HTTP. Works with Ollama's /v1 endpoint out of the box."""

    def __init__(self, config: LlmConfig, transport: Optional[httpx.BaseTransport] = None):
        self.config = config
        self.name = config.model
        headers = {"Content-Type": "application/json"}
        if config.api_key:
            headers["Authorization"] = f"Bearer {config.api_key}"
        self._client = httpx.Client(
            base_url=config.base_url, headers=headers,
            timeout=config.timeout_s, transport=transport)

    def chat(self, messages: list[dict], tools: list[dict]) -> ChatResult:
        payload = {
            "model": self.config.model,
            "messages": messages,
            "tools": tools,
            "tool_choice": "auto",
            "temperature": self.config.temperature,
            "max_tokens": self.config.max_tokens,
            "stream": False,
        }
        try:
            response = self._client.post("/chat/completions", json=payload)
            response.raise_for_status()
            body = response.json()
        except (httpx.HTTPError, ValueError) as error:
            raise LlmUnavailable(f"{type(error).__name__}: {error}") from error

        try:
            message = body["choices"][0]["message"]
        except (KeyError, IndexError, TypeError) as error:
            raise LlmUnavailable("Response had no choices[0].message") from error

        calls = []
        for index, raw in enumerate(message.get("tool_calls") or []):
            function = raw.get("function") or {}
            if not function.get("name"):
                continue
            arguments = function.get("arguments") or "{}"
            # The spec says a JSON string; some servers send the object itself.
            if not isinstance(arguments, str):
                arguments = json.dumps(arguments)
            calls.append(ToolCall(raw.get("id") or f"call_{index}", function["name"], arguments))

        usage = body.get("usage") or {}
        return ChatResult(
            content=message.get("content"),
            tool_calls=calls,
            prompt_tokens=int(usage.get("prompt_tokens") or 0),
            completion_tokens=int(usage.get("completion_tokens") or 0),
        )


def from_env() -> Optional[ChatModel]:
    """The configured model, or None when drafting is switched off."""
    config = LlmConfig.from_env()
    return OpenAICompatibleModel(config) if config.enabled else None
