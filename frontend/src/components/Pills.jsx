const PRIORITY_TONE = { P1: 'red', P2: 'amber', P3: 'blue', P4: 'grey' };
const STATUS_TONE = {
  NEW: 'blue',
  ASSIGNED: 'blue',
  IN_PROGRESS: 'amber',
  ON_HOLD: 'grey',
  RESOLVED: 'green',
  CLOSED: 'grey',
};

export function PriorityPill({ priority }) {
  if (!priority) return null;
  return <span className={`pill ${PRIORITY_TONE[priority] || 'grey'}`}>{priority}</span>;
}

export function StatusPill({ status }) {
  if (!status) return null;
  return (
    <span className={`pill ${STATUS_TONE[status] || 'grey'}`}>
      {status.replace('_', ' ').toLowerCase()}
    </span>
  );
}

/**
 * Risk is shown as a band rather than a percentage. A model that says "0.63" invites
 * an agent to read precision that isn't there.
 */
export function RiskPill({ risk }) {
  if (risk == null) return <span className="muted">—</span>;
  if (risk >= 0.66) return <span className="pill red">high risk</span>;
  if (risk >= 0.33) return <span className="pill amber">medium risk</span>;
  return <span className="pill green">low risk</span>;
}

export function TriagePill({ needsTriage }) {
  if (!needsTriage) return null;
  return <span className="pill amber">needs triage</span>;
}

export function formatHours(hours) {
  if (hours == null) return '—';
  if (hours < 1) return `${Math.round(hours * 60)} min`;
  return `${hours.toFixed(1)} hrs`;
}

export function formatWhen(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IE', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}
