import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { api } from './api';
import Dashboard from './pages/Dashboard';
import IncidentDetail from './pages/IncidentDetail';
import Incidents from './pages/Incidents';
import Login from './pages/Login';
import NewIncident from './pages/NewIncident';

export default function App() {
  const [user, setUser] = useState(null);
  const [checked, setChecked] = useState(false);

  // A 401 here is the normal "not signed in yet" case, not an error worth showing.
  useEffect(() => {
    api
      .me()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setChecked(true));
  }, []);

  if (!checked) return null;
  if (!user) return <Login onSignedIn={setUser} />;

  const isAgent = user.role === 'AGENT';

  async function signOut() {
    try {
      await api.logout();
    } finally {
      setUser(null);
    }
  }

  return (
    <>
      <header className="bar">
        <span className="brand">IntelliDesk</span>
        <nav>
          {isAgent && <NavLink to="/">Dashboard</NavLink>}
          <NavLink to="/incidents">{isAgent ? 'Queue' : 'My incidents'}</NavLink>
          <NavLink to="/new">Raise incident</NavLink>
          <span className="who">
            {user.name} · {isAgent ? 'Support agent' : 'Employee'}
          </span>
          <button className="secondary" style={{ margin: 0 }} onClick={signOut}>
            Sign out
          </button>
        </nav>
      </header>

      <main>
        <Routes>
          <Route
            path="/"
            element={isAgent ? <Dashboard /> : <Navigate to="/incidents" replace />}
          />
          <Route path="/incidents" element={<Incidents user={user} />} />
          <Route path="/incidents/:id" element={<IncidentDetail user={user} />} />
          <Route path="/new" element={<NewIncident />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}
