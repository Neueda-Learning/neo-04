// Thin fetch wrapper. Base is empty so paths are same-origin (nginx proxies in the
// container, Vite proxies in dev). Override with VITE_API_BASE if you must.
//
// Everything the UI calls goes through here on purpose: in the deployed stack the whole
// app is served under a path prefix (/neo-04) and VITE_API_BASE is how every URL
// picks it up. A raw fetch('/api/...') inside a component works on your laptop and 404s
// on the load balancer.
const BASE = import.meta.env.VITE_API_BASE || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

// Applications arrive from the orchestrator — the real one, or the sidecar playing it at
// http://localhost:9000 — never from a button in here. That is the contract: your module is
// called, it does not call itself. Screening configuration is different: it is this module's
// own reference data (the watchlist and country-risk lists), so the UI is allowed to write it.
export const api = {
  health: () => request('/health'),
  info: () => request('/info'),
  listApplications: () => request('/api/v1/applications'),
  searchCases: (keyword) =>
    request(`/api/v1/applications/cases?keyword=${encodeURIComponent(keyword)}`),
  getApplication: (id) => request(`/api/v1/applications/${id}`),
  searchCases: (q, limit = 10) => request(`/api/v1/cases?q=${encodeURIComponent(q)}&limit=${limit}`),

  // Screening configuration — insert-only, versioned. See docs/api-docs/screening-config-api-contract.md.
  listScreeningConfigs: () => request('/api/v1/screening-configs'),
  getCurrentScreeningConfig: () => request('/api/v1/screening-configs/current'),
  getScreeningConfigVersion: (version) => request(`/api/v1/screening-configs/${version}`),
  createScreeningConfig: (body) =>
    request('/api/v1/screening-configs', { method: 'POST', body: JSON.stringify(body) }),
  activateScreeningConfig: (version) =>
    request(`/api/v1/screening-configs/${version}/activate`, { method: 'PUT' }),
  deleteScreeningConfig: (version) =>
    request(`/api/v1/screening-configs/${version}`, { method: 'DELETE' }),
};
