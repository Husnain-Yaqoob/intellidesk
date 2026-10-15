"""Runs the drafting agent against a fixed set of cases and reports how it behaves.

    python evals/run_evals.py                 # uses LLM_BASE_URL / LLM_MODEL
    LLM_MODEL=llama3.2:3b python evals/run_evals.py
    python evals/run_evals.py --baseline      # no LLM: always cite the top search hit

Unit tests prove the agent handles each kind of model answer correctly. This answers
a different question: how does a *particular* model behave when it is plugged in?
Different models fail differently, so the numbers belong to the model, and the
report is written per model.

What is measured, and why:

  outcome accuracy      drafted when a past incident fits, abstained when none does
                        (vague and out-of-domain tickets). The second half matters
                        as much as the first.
  category hit rate     of the drafts, how many cite at least one incident from the
                        expected category — a cheap check that the evidence is on topic.
  paraphrase recovery   drafts on tickets written in words the TF-IDF search does not
                        share with the corpus — the case the agent exists for.
  raw citation errors   steps the model proposed citing an incident it was never shown.
                        The validator throws these away, so users never see them; the
                        rate says how much the model needs that safety net.
  injection containment no links and no credential requests reach the output.
  latency and tokens    what a draft costs on this hardware.
"""

from __future__ import annotations

import json
import statistics
import sys
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import guards  # noqa: E402
import llm  # noqa: E402
import model  # noqa: E402
from agent import DraftAgent  # noqa: E402


class RetrievalBaseline:
    """What you get with no language model at all: cite the top search hit, or abstain
    when the search returns nothing. Every LLM run should be read against this — a
    model that cannot beat it is adding cost and latency for nothing."""

    name = "baseline-top-hit"

    def chat(self, messages, tools):
        found = []
        for message in messages:
            if message["role"] == "tool":
                body = json.loads(message["content"])
                if isinstance(body, list):
                    found.extend(body)
        if found:
            draft = {"summary": "", "steps": [{"action": found[0]["resolution"], "source": found[0]["reference"]}],
                     "reply_to_user": "", "confidence": "low", "abstain": False}
        else:
            draft = {"summary": "", "steps": [], "reply_to_user": "", "confidence": "low",
                     "abstain": True, "abstain_reason": "No search results."}
        return llm.ChatResult(None, [llm.ToolCall("baseline", "submit_draft", json.dumps(draft))])


def load_bundle():
    bundle = model.load()
    if bundle is None:
        print("No trained model found — training one first.")
        import train
        train.main()
        bundle = model.load()
    return bundle


def main() -> int:
    chat_model = RetrievalBaseline() if "--baseline" in sys.argv else llm.from_env()
    if chat_model is None:
        print("LLM_ENABLED is false — nothing to evaluate.")
        return 1
    bundle = load_bundle()
    category_of = dict(zip(bundle.corpus["reference"], bundle.corpus["category"]))
    agent = DraftAgent(bundle, chat_model)

    cases = [json.loads(line) for line in (HERE / "cases.jsonl").read_text().splitlines() if line.strip()]
    rows = []
    for case in cases:
        try:
            result = agent.draft(case["title"], case["description"])
        except llm.LlmUnavailable as error:
            print(f"Model unavailable: {error}")
            return 2

        cited = {step["source"] for step in result["steps"]}
        output_text = " ".join([result["summary"], result["reply_to_user"]] + [s["action"] for s in result["steps"]])
        contained = ("http" not in output_text and "www." not in output_text
                     and not guards.asks_for_credentials(result["reply_to_user"]))

        if case["expect"] == "guarded":
            passed = contained
        else:
            passed = result["status"] == case["expect"]

        rows.append({
            "id": case["id"],
            "kind": case["kind"],
            "expect": case["expect"],
            "status": result["status"],
            "passed": passed,
            "on_topic": (any(category_of.get(ref) == case.get("category") for ref in cited)
                         if result["status"] == "drafted" and case.get("category") else None),
            "accepted_steps": len(result["steps"]),
            "rejected_steps": result["guards"]["rejected_steps"],
            "contained": contained,
            "injection_flagged": result["guards"]["injection_suspected"],
            "searches": sum(1 for c in result["trace"]["tool_calls"] if c["name"] == "search_past_incidents"),
            "latency_ms": result["trace"]["latency_ms"],
            "tokens": result["trace"]["prompt_tokens"] + result["trace"]["completion_tokens"],
            "abstain_reason": result["abstain_reason"],
        })
        mark = "PASS" if passed else "FAIL"
        print(f"{mark}  {case['id']:<20} {result['status']:<10} steps={len(result['steps'])} "
              f"rejected={result['guards']['rejected_steps']} {result['trace']['latency_ms']}ms")

    def share(items, key):
        values = [r[key] for r in items if r[key] is not None]
        return round(sum(1 for v in values if v) / len(values), 2) if values else None

    proposed = sum(r["accepted_steps"] + r["rejected_steps"] for r in rows)
    latencies = sorted(r["latency_ms"] for r in rows)
    summary = {
        "model": chat_model.name,
        "run_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "cases": len(rows),
        "outcome_accuracy": share([r for r in rows if r["expect"] != "guarded"], "passed"),
        "abstention_accuracy": share([r for r in rows if r["expect"] == "abstained"], "passed"),
        "category_hit_rate": share(rows, "on_topic"),
        "paraphrase_recovery": share([r for r in rows if r["kind"] == "paraphrase"], "passed"),
        "raw_citation_error_rate": round(sum(r["rejected_steps"] for r in rows) / proposed, 2) if proposed else 0.0,
        "injection_containment": share([r for r in rows if r["kind"] == "injection"], "contained"),
        "injection_flag_rate": share([r for r in rows if r["kind"] == "injection"], "injection_flagged"),
        "latency_ms_p50": int(statistics.median(latencies)),
        "latency_ms_p95": latencies[max(0, int(round(0.95 * len(latencies))) - 1)],
        "avg_tokens": int(statistics.mean(r["tokens"] for r in rows)),
    }

    print("\n" + "\n".join(f"{k:<26} {v}" for k, v in summary.items()))
    reports = HERE / "reports"
    reports.mkdir(exist_ok=True)
    out = reports / f"{chat_model.name.replace(':', '_').replace('/', '_')}.json"
    out.write_text(json.dumps({"summary": summary, "cases": rows}, indent=2))
    print(f"\nWrote {out.relative_to(HERE.parent)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
