import React, { useState } from 'react';
import { api, storeAuth } from './auth';

const T = {
  bg: '#f4f6f9',
  surface: '#ffffff',
  border: '#e2e6ec',
  accent: '#0a66c2',
  red: '#c4314b',
  text: '#1a2233',
  textDim: '#5b6679',
  textMuted: '#8b94a3',
  mono: "'IBM Plex Mono','SF Mono',Menlo,monospace",
  sans: "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif",
};

export default function Login({ onAuthenticated }) {
  const [mode, setMode] = useState('login'); // 'login' | 'register'
  const [form, setForm] = useState({ username: '', password: '', email: '', fullName: '', firm: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const endpoint = mode === 'login' ? '/api/auth/login' : '/api/auth/register';
      const payload = mode === 'login'
        ? { username: form.username, password: form.password }
        : { username: form.username, password: form.password, email: form.email, fullName: form.fullName, firm: form.firm };

      const res = await api.post(endpoint, payload);
      storeAuth(res.data);
      onAuthenticated(res.data);
    } catch (err) {
      const msg = err.response?.data?.message || 'Something went wrong. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      background: T.bg, minHeight: '100vh', display: 'flex', alignItems: 'center',
      justifyContent: 'center', fontFamily: T.sans,
    }}>
      <div style={{
        width: 380, background: T.surface, border: `1px solid ${T.border}`,
        borderRadius: 10, padding: 32, boxShadow: '0 4px 14px rgba(20,30,50,0.08)',
      }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{
            width: 44, height: 44, borderRadius: 9, background: 'rgba(10,102,194,0.08)',
            border: `1px solid ${T.accent}40`, display: 'flex', alignItems: 'center',
            justifyContent: 'center', color: T.accent, fontWeight: 800, fontSize: 18,
            fontFamily: T.mono, margin: '0 auto 12px',
          }}>PG</div>
          <div style={{ fontSize: 19, fontWeight: 700, color: T.text }}>PortfolioGuard</div>
          <div style={{ fontSize: 11, color: T.textMuted, letterSpacing: '0.06em', textTransform: 'uppercase', marginTop: 4 }}>
            Real-Time Portfolio Risk Intelligence
          </div>
        </div>

        <div style={{ display: 'flex', marginBottom: 20, background: T.bg, borderRadius: 8, padding: 4 }}>
          {['login', 'register'].map((m) => (
            <button key={m} onClick={() => { setMode(m); setError(''); }} style={{
              flex: 1, padding: '8px 0', border: 'none', borderRadius: 6, cursor: 'pointer',
              fontSize: 13, fontWeight: 600, fontFamily: T.sans,
              background: mode === m ? T.surface : 'transparent',
              color: mode === m ? T.accent : T.textMuted,
              boxShadow: mode === m ? '0 1px 3px rgba(20,30,50,0.08)' : 'none',
            }}>{m === 'login' ? 'Sign In' : 'Create Account'}</button>
          ))}
        </div>

        <form onSubmit={submit}>
          <Field label="Username" value={form.username} onChange={update('username')} required />
          {mode === 'register' && (
            <>
              <Field label="Email" type="email" value={form.email} onChange={update('email')} required />
              <Field label="Full Name" value={form.fullName} onChange={update('fullName')} />
              <Field label="Firm" value={form.firm} onChange={update('firm')} />
            </>
          )}
          <Field label="Password" type="password" value={form.password} onChange={update('password')} required
                 hint={mode === 'register' ? 'At least 8 characters' : undefined} />

          {error && (
            <div style={{ color: T.red, fontSize: 12.5, marginBottom: 14, padding: '8px 10px', background: 'rgba(196,49,75,0.08)', borderRadius: 6 }}>
              {error}
            </div>
          )}

          <button type="submit" disabled={loading} style={{
            width: '100%', padding: '11px 0', background: T.accent, color: '#fff',
            border: 'none', borderRadius: 7, fontSize: 14, fontWeight: 600,
            cursor: loading ? 'default' : 'pointer', opacity: loading ? 0.7 : 1,
            fontFamily: T.sans,
          }}>
            {loading ? 'Please wait…' : mode === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>
      </div>
    </div>
  );
}

function Field({ label, type = 'text', value, onChange, required, hint }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{ display: 'block', fontSize: 11.5, fontWeight: 600, color: T.textDim, marginBottom: 5 }}>
        {label}
      </label>
      <input
        type={type}
        value={value}
        onChange={onChange}
        required={required}
        style={{
          width: '100%', padding: '9px 12px', border: `1px solid ${T.border}`,
          borderRadius: 6, fontSize: 13.5, fontFamily: T.sans, color: T.text,
          outline: 'none', boxSizing: 'border-box',
        }}
      />
      {hint && <div style={{ fontSize: 10.5, color: T.textMuted, marginTop: 4 }}>{hint}</div>}
    </div>
  );
}
