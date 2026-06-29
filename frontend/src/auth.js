import axios from 'axios';

export const API_BASE = 'http://localhost:8080';

// Single axios instance used everywhere. The Authorization header is
// attached automatically via the request interceptor below, so existing
// call sites (api.get(...), api.post(...)) don't need to pass it manually.
export const api = axios.create({ baseURL: API_BASE });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('pg_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// If a request ever comes back 401 (expired/invalid token), force logout
// so the user sees the login screen instead of a silently broken dashboard.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('pg_token');
      localStorage.removeItem('pg_user');
      window.location.reload();
    }
    return Promise.reject(error);
  }
);

export function getStoredUser() {
  try {
    const raw = localStorage.getItem('pg_user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function storeAuth(authResponse) {
  localStorage.setItem('pg_token', authResponse.token);
  localStorage.setItem('pg_user', JSON.stringify({
    userId: authResponse.userId,
    username: authResponse.username,
    email: authResponse.email,
    role: authResponse.role,
  }));
}

export function clearAuth() {
  localStorage.removeItem('pg_token');
  localStorage.removeItem('pg_user');
}

export function isLoggedIn() {
  return !!localStorage.getItem('pg_token');
}
