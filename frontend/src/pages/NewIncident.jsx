import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';

export default function NewIncident() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    title: '',
    description: '',
    impact: 'MEDIUM',
    urgency: 'MEDIUM',
  });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await api.createIncident(form);
      navigate(`/incidents/${created.summary.id}`);
    } catch (err) {
      setError(err.detail || 'Could not raise the incident');
      setBusy(false);
    }
  }

  return (
    <>
      <h1>Raise an incident</h1>
      <p className="sub">
        Describe what is happening in your own words. Priority is worked out from impact
        and urgency, not guessed.
      </p>

      {error && <div className="error">{error}</div>}

      <div className="card" style={{ maxWidth: 640 }}>
        <form onSubmit={submit}>
          <label htmlFor="title">Title</label>
          <input
            id="title"
            value={form.title}
            maxLength={200}
            placeholder="VPN disconnecting repeatedly"
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            required
          />

          <label htmlFor="description">What is happening?</label>
          <textarea
            id="description"
            value={form.description}
            placeholder="My VPN disconnects every ten minutes when working from home."
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            required
          />

          <div className="row">
            <div>
              <label htmlFor="impact">Impact — how many people are affected?</label>
              <select
                id="impact"
                value={form.impact}
                onChange={(e) => setForm({ ...form, impact: e.target.value })}
              >
                <option value="HIGH">High — a team or site</option>
                <option value="MEDIUM">Medium — a few people</option>
                <option value="LOW">Low — just me</option>
              </select>
            </div>
            <div>
              <label htmlFor="urgency">Urgency — can you keep working?</label>
              <select
                id="urgency"
                value={form.urgency}
                onChange={(e) => setForm({ ...form, urgency: e.target.value })}
              >
                <option value="HIGH">High — completely blocked</option>
                <option value="MEDIUM">Medium — slowed down</option>
                <option value="LOW">Low — an annoyance</option>
              </select>
            </div>
          </div>

          <button type="submit" disabled={busy}>
            {busy ? 'Submitting…' : 'Submit incident'}
          </button>
        </form>
      </div>
    </>
  );
}
