import { useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../api';
import { formatHours } from '../components/Pills';

export default function Dashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.dashboard().then(setData).catch((err) => setError(err.detail || 'Could not load dashboard'));
  }, []);

  if (error) return <div className="error">{error}</div>;
  if (!data) return <p className="muted">Loading…</p>;

  return (
    <>
      <h1>Service desk</h1>
      <p className="sub">Where the queue stands right now.</p>

      {!data.mlServiceUp && (
        <div className="banner">
          The classification service is offline. Incidents are still being logged and are
          going to the triage queue for manual routing.
        </div>
      )}

      <div className="tiles">
        <div className="tile"><div className="n">{data.openIncidents}</div><div className="l">Open</div></div>
        <div className="tile red"><div className="n">{data.criticalOpen}</div><div className="l">Critical open</div></div>
        <div className="tile amber"><div className="n">{data.needingTriage}</div><div className="l">Awaiting triage</div></div>
        <div className="tile green"><div className="n">{data.resolvedToday}</div><div className="l">Resolved today</div></div>
        <div className="tile">
          <div className="n">{formatHours(data.averageResolutionHours)}</div>
          <div className="l">Avg resolution</div>
        </div>
      </div>

      <div className="card">
        <h2>Incidents by category</h2>
        {data.byCategory.length === 0 ? (
          <div className="empty">Nothing classified yet.</div>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={data.byCategory} layout="vertical" margin={{ left: 30, right: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#eef0f3" horizontal={false} />
              <XAxis type="number" stroke="#98a2b3" fontSize={12} allowDecimals={false} />
              <YAxis type="category" dataKey="category" stroke="#667085" fontSize={12} width={110} />
              <Tooltip cursor={{ fill: '#f7f8fa' }} />
              <Bar dataKey="count" fill="#1d4ed8" radius={[0, 4, 4, 0]} name="Incidents" />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="card">
        <h2>Raised and resolved, last 14 days</h2>
        <ResponsiveContainer width="100%" height={240}>
          <LineChart data={data.trend} margin={{ left: 0, right: 20 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#eef0f3" />
            <XAxis
              dataKey="date"
              stroke="#98a2b3"
              fontSize={11}
              tickFormatter={(value) => value.slice(5)}
            />
            <YAxis stroke="#98a2b3" fontSize={12} allowDecimals={false} />
            <Tooltip />
            <Legend />
            <Line type="monotone" dataKey="created" stroke="#b54708" strokeWidth={2} dot={false} name="Raised" />
            <Line type="monotone" dataKey="resolved" stroke="#067647" strokeWidth={2} dot={false} name="Resolved" />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="card">
        <h2>By priority</h2>
        <table>
          <thead>
            <tr><th>Priority</th><th>Incidents</th></tr>
          </thead>
          <tbody>
            {data.byPriority.map((row) => (
              <tr key={row.priority}>
                <td>{row.priority}</td>
                <td>{row.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
