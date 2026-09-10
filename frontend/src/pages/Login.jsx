import { useState } from 'react';
import { api } from '../api';

export default function Login({ onSignedIn }) {
  const [email, setEmail] = useState('agent@intellidesk.ie');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.primeCsrf();
      const user = await api.login(email, password);
      onSignedIn(user);
    } catch (err) {
      setError(err.detail || 'Could not sign in');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <div className="card">
        <h1>IntelliDesk</h1>
        <p className="sub">IT incident management</p>

        {error && <div className="error">{error}</div>}

        <form onSubmit={submit}>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />

          <button type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <div className="demo-hint">
          Demo accounts (password <code>password123</code>):<br />
          <strong>agent@intellidesk.ie</strong> — support agent, sees everything<br />
          <strong>employee@intellidesk.ie</strong> — employee, sees only their own
        </div>
      </div>
    </div>
  );
}
