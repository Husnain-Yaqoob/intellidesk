import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api';
import {
  PriorityPill,
  RiskPill,
  StatusPill,
  TriagePill,
  formatHours,
  formatWhen,
} from '../components/Pills';

export default function IncidentDetail({ user }) {
  const { id } = useParams();
  const [detail, setDetail] = useState(null);
  const [teams, setTeams] = useState([]);
  const [error, setError] = useState(null);
  const [note, setNote] = useState('');
  const [resolution, setResolution] = useState('');

  const isAgent = user.role === 'AGENT';

  const load = useCallback(() => {
    api.incident(id).then(setDetail).catch((err) => setError(err.detail || 'Could not load'));
  }, [id]);

  useEffect(load, [load]);

  useEffect(() => {
    if (isAgent) api.teams().then(setTeams).catch(() => setTeams([]));
  }, [isAgent]);

  async function run(action) {
    setError(null);
    try {
      setDetail(await action());
    } catch (err) {
      setError(err.detail || 'That did not work');
    }
  }

  if (error && !detail) return <div className="error">{error}</div>;
  if (!detail) return <p className="muted">Loading…</p>;

  const { summary } = detail;

  return (
    <>
      <h1>
        {summary.reference} — {summary.title}
      </h1>
      <p className="sub">
        <PriorityPill priority={summary.priority} /> <StatusPill status={summary.status} />{' '}
        <TriagePill needsTriage={summary.needsTriage} />{' '}
        <span className="muted">
          {summary.category || 'Uncategorised'}
          {detail.subcategory ? ` / ${detail.subcategory}` : ''} ·{' '}
          {summary.assignedTeam || 'Unassigned'} · raised by {summary.createdBy}
        </span>
      </p>

      {error && <div className="error">{error}</div>}

      <div className="split">
        <div>
          <div className="card">
            <h2>What was reported</h2>
            <p style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{detail.description}</p>
            <p className="muted" style={{ marginTop: 14 }}>
              Impact {detail.impact.toLowerCase()} · urgency {detail.urgency.toLowerCase()} ·
              SLA target {detail.slaTargetHours} hrs · raised {formatWhen(summary.createdAt)}
            </p>
          </div>

          {detail.suggestedSteps?.length > 0 && (
            <div className="card">
              <h2>Suggested first steps</h2>
              <p className="muted" style={{ marginTop: -8 }}>
                Taken from how similar incidents were actually resolved — nothing here is
                generated.
              </p>
              <ol style={{ margin: '10px 0 0', paddingLeft: 20 }}>
                {detail.suggestedSteps.map((step) => (
                  <li key={step} style={{ marginBottom: 6 }}>{step}</li>
                ))}
              </ol>
            </div>
          )}

          {detail.similarIncidents?.length > 0 && (
            <div className="card">
              <h2>Similar past incidents</h2>
              <table>
                <thead>
                  <tr><th>Reference</th><th>Title</th><th>Resolved by</th><th>Match</th></tr>
                </thead>
                <tbody>
                  {detail.similarIncidents.map((similar) => (
                    <tr key={similar.reference}>
                      <td>{similar.reference}</td>
                      <td>{similar.title}</td>
                      <td>{similar.resolution}</td>
                      <td>{Math.round(similar.similarity * 100)}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="card">
            <h2>Activity</h2>
            <ul className="activity">
              {detail.activity.map((entry, index) => (
                <li key={index}>
                  <div className="when">
                    {formatWhen(entry.at)} · {entry.user}
                  </div>
                  {entry.comment}
                </li>
              ))}
            </ul>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!note.trim()) return;
                run(() => api.addNote(id, note)).then(() => setNote(''));
              }}
            >
              <label htmlFor="note">Add a note</label>
              <textarea id="note" value={note} onChange={(e) => setNote(e.target.value)} />
              <button type="submit" disabled={!note.trim()}>Add note</button>
            </form>
          </div>
        </div>

        <div>
          <div className="card">
            <h2>Model analysis</h2>
            {summary.category ? (
              <>
                <p className="muted" style={{ margin: '0 0 10px' }}>
                  Classified as <strong>{summary.category}</strong>
                  {detail.categoryConfidence != null &&
                    ` at ${Math.round(detail.categoryConfidence * 100)}% confidence`}
                </p>
              </>
            ) : (
              <p className="muted" style={{ margin: '0 0 10px' }}>
                Not classified — this one is waiting on a human.
              </p>
            )}
            <table>
              <tbody>
                <tr>
                  <td className="muted">Predicted time</td>
                  <td>{formatHours(summary.predictedResolutionHours)}</td>
                </tr>
                <tr>
                  <td className="muted">SLA risk</td>
                  <td><RiskPill risk={summary.slaBreachRisk} /></td>
                </tr>
                {detail.actualResolutionHours != null && (
                  <tr>
                    <td className="muted">Actual time</td>
                    <td>{formatHours(detail.actualResolutionHours)}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {isAgent && (
            <div className="card">
              <h2>Actions</h2>

              <label htmlFor="team">Assign to team</label>
              <select
                id="team"
                defaultValue=""
                onChange={(e) => e.target.value && run(() => api.assign(id, Number(e.target.value)))}
              >
                <option value="">Choose a team…</option>
                {teams.map((team) => (
                  <option key={team.id} value={team.id}>{team.name}</option>
                ))}
              </select>

              <label htmlFor="status">Move to</label>
              <select
                id="status"
                defaultValue=""
                onChange={(e) => e.target.value && run(() => api.changeStatus(id, e.target.value))}
              >
                <option value="">Choose a status…</option>
                {detail.allowedTransitions.map((status) => (
                  <option key={status} value={status}>
                    {status.replace('_', ' ').toLowerCase()}
                  </option>
                ))}
              </select>

              {summary.status !== 'RESOLVED' && summary.status !== 'CLOSED' && (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (!resolution.trim()) return;
                    run(() => api.resolve(id, resolution)).then(() => setResolution(''));
                  }}
                >
                  <label htmlFor="resolution">Resolution</label>
                  <textarea
                    id="resolution"
                    placeholder="What actually fixed it?"
                    value={resolution}
                    onChange={(e) => setResolution(e.target.value)}
                  />
                  <button type="submit" disabled={!resolution.trim()}>Resolve incident</button>
                </form>
              )}
            </div>
          )}

          {detail.resolutionText && (
            <div className="card">
              <h2>Resolution</h2>
              <p style={{ margin: 0 }}>{detail.resolutionText}</p>
              <p className="muted" style={{ marginTop: 10 }}>
                Resolved {formatWhen(detail.resolvedAt)} · took{' '}
                {formatHours(detail.actualResolutionHours)}
              </p>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
