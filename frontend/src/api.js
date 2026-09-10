/**
 * Thin API client.
 *
 * Two things it exists to get right:
 *  - every request carries the session cookie (credentials: 'include')
 *  - every mutating request echoes the CSRF token Spring Security set as a cookie
 */

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export class ApiError extends Error {
  constructor(status, detail) {
    super(detail || `Request failed (${status})`);
    this.status = status;
    this.detail = detail;
  }
}

async function request(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  if (method !== 'GET') {
    const token = csrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) return null;

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new ApiError(response.status, payload?.detail);
  }
  return payload;
}

export const api = {
  // The backend only issues the CSRF cookie once something asks for it.
  primeCsrf: () => request('/auth/csrf'),
  login: (email, password) => request('/auth/login', { method: 'POST', body: { email, password } }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  me: () => request('/auth/me'),

  incidents: (params = {}) => {
    const query = new URLSearchParams(
      Object.entries(params).filter(([, value]) => value !== '' && value != null),
    );
    return request(`/incidents${query.toString() ? `?${query}` : ''}`);
  },
  incident: (id) => request(`/incidents/${id}`),
  createIncident: (payload) => request('/incidents', { method: 'POST', body: payload }),
  addNote: (id, comment) => request(`/incidents/${id}/notes`, { method: 'POST', body: { comment } }),
  changeStatus: (id, status) => request(`/incidents/${id}/status`, { method: 'POST', body: { status } }),
  assign: (id, teamId) => request(`/incidents/${id}/assign`, { method: 'POST', body: { teamId } }),
  resolve: (id, resolutionText) =>
    request(`/incidents/${id}/resolve`, { method: 'POST', body: { resolutionText } }),

  teams: () => request('/teams'),
  dashboard: () => request('/dashboard'),
};
