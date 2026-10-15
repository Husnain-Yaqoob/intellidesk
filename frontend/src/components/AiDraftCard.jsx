import { useState } from 'react';
import { formatWhen } from './Pills';

/**
 * AI-assisted draft, for agents only.
 *
 * Everything on this card is framed as a draft to check, not an answer: each step
 * names the past incident it came from, guard flags are shown rather than hidden,
 * and an abstention is presented as a result in its own right.
 */
export default function AiDraftCard({ draft, draftedAt, onDraft }) {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState(null);
  const [copied, setCopied] = useState(false);

  async function requestDraft() {
    setBusy(true);
    setProblem(null);
    try {
      await onDraft();
    } catch (err) {
      setProblem(err.status === 503
        ? 'AI drafting is not available right now — the language model is not running. Nothing else is affected.'
        : err.detail || 'The draft request failed.');
    } finally {
      setBusy(false);
    }
  }

  async function copyReply() {
    try {
      await navigator.clipboard.writeText(draft.reply_to_user ?? '');
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard blocked — the text is on screen anyway */
    }
  }

  const steps = draft?.steps ?? [];
  const guards = draft?.guards ?? {};
  const trace = draft?.trace ?? {};
  const reply = draft?.reply_to_user ?? '';
  const reason = draft?.abstain_reason ?? '';

  return (
    <div className="card">
      <h2>AI-assisted draft</h2>
      <p className="muted" style={{ marginTop: -8 }}>
        {/* Field names are snake_case: they arrive exactly as the ML service wrote them. */}
        A language model reads the past incidents found for this ticket and drafts first
        steps. Every step must cite the incident it came from; anything it cannot ground is
        thrown away. Check it before acting on it.
      </p>

      {guards.injection_suspected && (
        <div className="banner">
          This ticket contains text that looks like an attempt to instruct the AI
          ({(guards.injection_signals ?? []).join(', ')}). The draft was produced with the
          ticket treated as data, but read it with that in mind.
        </div>
      )}

      {draft && draft.status === 'drafted' && (
        <>
          {draft.summary && <p style={{ margin: '4px 0 10px' }}><strong>{draft.summary}</strong></p>}
          <ol style={{ margin: '0 0 12px', paddingLeft: 20 }}>
            {steps.map((step) => (
              <li key={`${step.source}-${step.action}`} style={{ marginBottom: 8 }}>
                {step.action}
                <div className="muted">
                  from {step.source} — {step.source_title}
                </div>
              </li>
            ))}
          </ol>

          {reply ? (
            <>
              <label>Suggested reply to the user</label>
              <p style={{ margin: '4px 0 8px', whiteSpace: 'pre-wrap' }}>{reply}</p>
              <button type="button" className="secondary" onClick={copyReply}>
                {copied ? 'Copied' : 'Copy reply'}
              </button>
            </>
          ) : guards.reply_withheld ? (
            <p className="muted">The suggested reply was withheld because it asked the user for credentials.</p>
          ) : null}
        </>
      )}

      {draft && draft.status === 'abstained' && (
        <p style={{ margin: '4px 0 10px' }}>
          <strong>No draft.</strong> {reason || 'No past incident matched closely enough.'}{' '}
          <span className="muted">That is a deliberate outcome, not a failure — this one needs a human.</span>
        </p>
      )}

      {draft && (
        <p className="muted" style={{ marginTop: 12 }}>
          {trace.model} · {formatWhen(draftedAt)} · {trace.tool_calls?.length ?? 0} tool calls ·{' '}
          {((trace.latency_ms ?? 0) / 1000).toFixed(1)}s
          {guards.rejected_steps > 0 && ` · ${guards.rejected_steps} ungrounded step(s) discarded`}
          {guards.links_removed > 0 && ` · ${guards.links_removed} link(s) removed`}
          {guards.redactions > 0 && ` · ${guards.redactions} personal detail(s) redacted before sending`}
        </p>
      )}

      {problem && <div className="error">{problem}</div>}

      <button type="button" onClick={requestDraft} disabled={busy} style={{ marginTop: 10 }}>
        {busy ? 'Drafting… (a local model can take a minute)' : draft ? 'Draft again' : 'Draft with AI'}
      </button>
    </div>
  );
}
