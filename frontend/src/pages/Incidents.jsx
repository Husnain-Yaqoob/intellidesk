import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { PriorityPill, RiskPill, StatusPill, TriagePill, formatWhen } from '../components/Pills';

const STATUSES = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED'];

export default function Incidents({ user }) {
  const navigate = useNavigate();
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);
  const [filters, setFilters] = useState({ status: '', q: '', openOnly: true });

  useEffect(() => {
    let cancelled = false;
    api
      .incidents({ status: filters.status, q: filters.q, openOnly: filters.openOnly })
      .then((result) => !cancelled && setPage(result))
      .catch((err) => !cancelled && setError(err.detail || 'Could not load incidents'));
    return () => {
      cancelled = true;
    };
  }, [filters]);

  const isAgent = user.role === 'AGENT';

  return (
    <>
      <h1>{isAgent ? 'Incident queue' : 'My incidents'}</h1>
      <p className="sub">
        {isAgent
          ? 'Everything open, most urgent first.'
          : 'Incidents you have raised.'}
      </p>

      <div className="card">
        <div className="filters">
          <div>
            <label htmlFor="q">Search</label>
            <input
              id="q"
              placeholder="Reference, title or description"
              value={filters.q}
              onChange={(e) => setFilters({ ...filters, q: e.target.value })}
            />
          </div>
          <div>
            <label htmlFor="status">Status</label>
            <select
              id="status"
              value={filters.status}
              onChange={(e) => setFilters({ ...filters, status: e.target.value })}
            >
              <option value="">Any</option>
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status.replace('_', ' ').toLowerCase()}
                </option>
              ))}
            </select>
          </div>
          <div style={{ flex: '0 0 auto' }}>
            <label htmlFor="openOnly">Open only</label>
            <input
              id="openOnly"
              type="checkbox"
              style={{ width: 'auto' }}
              checked={filters.openOnly}
              onChange={(e) => setFilters({ ...filters, openOnly: e.target.checked })}
            />
          </div>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="card">
        {!page ? (
          <p className="muted">Loading…</p>
        ) : page.content.length === 0 ? (
          <div className="empty">No incidents match those filters.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Reference</th>
                <th>Title</th>
                <th>Category</th>
                <th>Priority</th>
                <th>Status</th>
                {isAgent && <th>SLA</th>}
                <th>Raised</th>
              </tr>
            </thead>
            <tbody>
              {page.content.map((incident) => (
                <tr
                  key={incident.id}
                  className="clickable"
                  onClick={() => navigate(`/incidents/${incident.id}`)}
                >
                  <td>{incident.reference}</td>
                  <td>
                    {incident.title}
                    <div className="muted">{incident.assignedTeam || 'Unassigned'}</div>
                  </td>
                  <td>
                    {incident.category || <span className="muted">—</span>}{' '}
                    <TriagePill needsTriage={incident.needsTriage} />
                  </td>
                  <td><PriorityPill priority={incident.priority} /></td>
                  <td><StatusPill status={incident.status} /></td>
                  {isAgent && <td><RiskPill risk={incident.slaBreachRisk} /></td>}
                  <td className="muted">{formatWhen(incident.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {page && page.totalElements > page.content.length && (
        <p className="muted">
          Showing {page.content.length} of {page.totalElements}.
        </p>
      )}
    </>
  );
}
