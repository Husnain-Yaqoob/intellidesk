"""Safeguards around the language model, on the way in and on the way out.

A ticket is text written by anyone in the company, and some of it will be written
by someone who has read about prompt injection. So the model is never trusted with
anything it could misuse, and its output is never trusted until it has been checked:

On the way in
  - Personal data that the model does not need (email addresses, phone numbers,
    anything that looks like a password or a card number) is redacted before the
    prompt is built. With Ollama the text never leaves the machine anyway; with a
    hosted model this is the difference between sending a password to a third party
    and not.
  - The ticket is wrapped in <ticket> tags and described to the model as data, and
    any tags the user typed themselves are neutralised so they cannot close the
    wrapper early.
  - Common injection phrasings are detected and *reported* to the agent in the UI.
    Detection is a heuristic and will miss things; that is why it is not the defence.

On the way out — the actual defence
  - Links are stripped from everything the model writes. A draft reply is something
    an agent might paste to a user, and a link in it is the obvious payload.
  - A reply that asks the user for a password, PIN or one-time code is withheld
    entirely. No legitimate service-desk reply does that.
  - Lengths are capped.
  - Citation checking lives in agent.py: a step that does not cite an incident the
    tools actually returned is thrown away.
"""

from __future__ import annotations

import re

# ---------------------------------------------------------------------------
# Inbound
# ---------------------------------------------------------------------------

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
# Card-length digit runs first, so they are not half-eaten by the phone pattern.
_LONG_NUMBER = re.compile(r"\b(?:\d[ -]?){13,19}\b")
_PHONE = re.compile(r"(?<![\w.])\+?\d[\d\s()\-]{7,}\d(?![\w.])")
_SECRET = re.compile(
    r"\b(password|passwd|pwd|passcode|pin|secret|api[_ ]?key|token)\b(\s*(?:is|=|:)\s*)(\S+)",
    re.IGNORECASE)


def redact(text: str) -> tuple[str, int]:
    """Returns the text with personal data replaced, and how many replacements were made."""
    if not text:
        return "", 0
    count = 0

    def sub(pattern: re.Pattern, replacement, value: str) -> str:
        nonlocal count
        value, n = pattern.subn(replacement, value)
        count += n
        return value

    text = sub(_SECRET, lambda m: f"{m.group(1)}{m.group(2)}[REDACTED]", text)
    text = sub(_EMAIL, "[EMAIL]", text)
    text = sub(_LONG_NUMBER, "[NUMBER]", text)
    text = sub(_PHONE, "[PHONE]", text)
    return text, count


_INJECTION_SIGNALS = {
    "override-instructions": re.compile(
        r"\b(ignore|disregard|forget|override)\b[\w\s,]{0,30}\b(instruction|instructions|rules|prompt|above|previous)\b",
        re.IGNORECASE),
    "role-play": re.compile(r"\b(you are now|act as|pretend to be|from now on you)\b", re.IGNORECASE),
    "prompt-extraction": re.compile(
        r"\b(system prompt|your instructions|reveal (?:your|the) prompt|developer mode)\b", re.IGNORECASE),
    "fake-markup": re.compile(r"</?\s*(system|assistant|ticket|tool)\s*>", re.IGNORECASE),
}


def injection_signals(text: str) -> list[str]:
    """Names of the injection patterns found. Empty when nothing looks suspicious."""
    return [name for name, pattern in _INJECTION_SIGNALS.items() if pattern.search(text or "")]


def wrap_ticket(title: str, description: str) -> str:
    """Puts untrusted text inside a wrapper it cannot break out of."""
    def neutralise(value: str) -> str:
        return re.sub(r"<\s*/?\s*ticket\s*>", "[tag removed]", value or "", flags=re.IGNORECASE)

    return (
        "<ticket>\n"
        f"Title: {neutralise(title).strip()}\n"
        f"Description: {neutralise(description).strip()}\n"
        "</ticket>"
    )


# ---------------------------------------------------------------------------
# Outbound
# ---------------------------------------------------------------------------

_URL = re.compile(r"(?:https?://|www\.)\S+", re.IGNORECASE)
_CREDENTIAL_REQUEST = re.compile(
    r"\b(send|share|provide|give|tell|email|reply with|confirm|enter|type)\b[^.!?\n]{0,60}"
    r"\b(password|passcode|pin|one[- ]time code|verification code|mfa code|security code|credentials)\b",
    re.IGNORECASE)


def strip_links(text: str) -> tuple[str, int]:
    return _URL.subn("[link removed]", text or "")


def asks_for_credentials(text: str) -> bool:
    return bool(_CREDENTIAL_REQUEST.search(text or ""))


def clamp(text: str, limit: int) -> str:
    text = (text or "").strip()
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"
