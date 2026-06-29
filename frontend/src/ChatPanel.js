import React, { useState, useEffect, useRef, useCallback } from 'react';
import { api } from './auth';

const T = {
  surface: '#ffffff',
  surfaceAlt: '#f8f9fb',
  border: '#e2e6ec',
  accent: '#0a66c2',
  green: '#0a8754',
  red: '#c4314b',
  text: '#1a2233',
  textDim: '#5b6679',
  textMuted: '#8b94a3',
  mono: "'IBM Plex Mono','SF Mono',Menlo,monospace",
  sans: "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif",
};

export default function ChatPanel({ selectedPortfolioId }) {
  const [open, setOpen] = useState(false);
  const [sessions, setSessions] = useState([]);
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [view, setView] = useState('list'); // 'list' | 'chat'
  const scrollRef = useRef(null);

  const loadSessions = useCallback(() => {
    setLoadingSessions(true);
    api.get('/api/chat/sessions')
      .then((res) => setSessions(res.data))
      .catch(() => setSessions([]))
      .finally(() => setLoadingSessions(false));
  }, []);

  useEffect(() => {
    if (open) loadSessions();
  }, [open, loadSessions]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const openSession = async (id) => {
    setActiveSessionId(id);
    setView('chat');
    try {
      const res = await api.get(`/api/chat/sessions/${id}`);
      setMessages(res.data.messages || []);
    } catch {
      setMessages([]);
    }
  };

  const startNewSession = async () => {
    try {
      const res = await api.post('/api/chat/sessions', { title: 'New conversation' });
      setSessions((prev) => [res.data, ...prev]);
      setActiveSessionId(res.data.id);
      setMessages([]);
      setView('chat');
    } catch (err) {
      console.error('Failed to start session', err);
    }
  };

  const deleteSession = async (id, e) => {
    e.stopPropagation();
    try {
      await api.delete(`/api/chat/sessions/${id}`);
      setSessions((prev) => prev.filter((s) => s.id !== id));
      if (activeSessionId === id) {
        setActiveSessionId(null);
        setView('list');
      }
    } catch (err) {
      console.error('Failed to delete session', err);
    }
  };

  const sendMessage = async (e) => {
    e.preventDefault();
    if (!input.trim() || !activeSessionId) return;
    const userText = input.trim();
    setInput('');
    setSending(true);

    setMessages((prev) => [...prev, { id: `temp-${Date.now()}`, role: 'user', content: userText, createdAt: new Date().toISOString() }]);

    try {
      const res = await api.post(`/api/chat/sessions/${activeSessionId}/messages`, {
        content: userText,
        portfolioId: selectedPortfolioId,
      });
      setMessages((prev) => [...prev, res.data]);
      loadSessions(); // refresh title/order in case it auto-titled
    } catch (err) {
      setMessages((prev) => [...prev, {
        id: `err-${Date.now()}`, role: 'assistant',
        content: 'Sorry, something went wrong sending that message.',
        createdAt: new Date().toISOString(),
      }]);
    } finally {
      setSending(false);
    }
  };

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} style={{
        position: 'fixed', bottom: 24, right: 24, width: 54, height: 54, borderRadius: '50%',
        background: T.accent, color: '#fff', border: 'none', fontSize: 22, cursor: 'pointer',
        boxShadow: '0 4px 14px rgba(10,102,194,0.35)', zIndex: 300,
      }}>💬</button>
    );
  }

  return (
    <div style={{
      position: 'fixed', bottom: 24, right: 24, width: 360, height: 520,
      background: T.surface, border: `1px solid ${T.border}`, borderRadius: 12,
      boxShadow: '0 8px 30px rgba(20,30,50,0.18)', display: 'flex', flexDirection: 'column',
      overflow: 'hidden', zIndex: 300, fontFamily: T.sans,
    }}>
      {/* Header */}
      <div style={{
        padding: '12px 14px', borderBottom: `1px solid ${T.border}`, display: 'flex',
        alignItems: 'center', justifyContent: 'space-between', background: T.surfaceAlt,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {view === 'chat' && (
            <button onClick={() => setView('list')} style={{
              border: 'none', background: 'transparent', cursor: 'pointer', fontSize: 14, color: T.textDim, padding: 0,
            }}>←</button>
          )}
          <span style={{ fontSize: 13, fontWeight: 700, color: T.text }}>
            {view === 'list' ? 'Risk Assistant' : (sessions.find((s) => s.id === activeSessionId)?.title || 'Conversation')}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {view === 'list' && (
            <button onClick={startNewSession} style={{
              fontSize: 11, padding: '4px 9px', borderRadius: 5, border: `1px solid ${T.accent}50`,
              background: 'transparent', color: T.accent, cursor: 'pointer', fontWeight: 600,
            }}>+ New</button>
          )}
          <button onClick={() => setOpen(false)} style={{
            border: 'none', background: 'transparent', cursor: 'pointer', fontSize: 16, color: T.textMuted, padding: 0,
          }}>×</button>
        </div>
      </div>

      {/* Body */}
      {view === 'list' ? (
        <div style={{ flex: 1, overflowY: 'auto', padding: 10 }}>
          {loadingSessions && <div style={{ fontSize: 12, color: T.textMuted, padding: 10 }}>Loading…</div>}
          {!loadingSessions && sessions.length === 0 && (
            <div style={{ fontSize: 12.5, color: T.textMuted, padding: '20px 10px', textAlign: 'center' }}>
              No conversations yet. Start a new one to ask about your portfolio's risk, VaR, Beta, or any stock ticker.
            </div>
          )}
          {sessions.map((s) => (
            <div key={s.id} onClick={() => openSession(s.id)} style={{
              padding: '10px 12px', borderRadius: 7, background: T.surfaceAlt, marginBottom: 6,
              cursor: 'pointer', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            }}>
              <div style={{ overflow: 'hidden' }}>
                <div style={{ fontSize: 12.5, color: T.text, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {s.title}
                </div>
                <div style={{ fontSize: 10, color: T.textMuted, marginTop: 2 }}>
                  {new Date(s.updatedAt).toLocaleString()}
                </div>
              </div>
              <button onClick={(e) => deleteSession(s.id, e)} style={{
                border: 'none', background: 'transparent', color: T.red, cursor: 'pointer', fontSize: 14, padding: '0 4px',
              }}>🗑</button>
            </div>
          ))}
        </div>
      ) : (
        <>
          <div ref={scrollRef} style={{ flex: 1, overflowY: 'auto', padding: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {messages.length === 0 && (
              <div style={{ fontSize: 12, color: T.textMuted, textAlign: 'center', marginTop: 20 }}>
                Ask me about your portfolio's risk, VaR, Sharpe ratio, Beta, anomalies, or any stock ticker.
              </div>
            )}
            {messages.map((m) => (
              <div key={m.id} style={{
                alignSelf: m.role === 'user' ? 'flex-end' : 'flex-start',
                maxWidth: '85%',
                background: m.role === 'user' ? T.accent : T.surfaceAlt,
                color: m.role === 'user' ? '#fff' : T.text,
                padding: '9px 12px', borderRadius: 10, fontSize: 12.5, lineHeight: 1.45,
              }}>
                {m.content}
              </div>
            ))}
            {sending && (
              <div style={{ alignSelf: 'flex-start', fontSize: 11.5, color: T.textMuted, fontStyle: 'italic' }}>
                Thinking…
              </div>
            )}
          </div>
          <form onSubmit={sendMessage} style={{ display: 'flex', gap: 6, padding: 10, borderTop: `1px solid ${T.border}` }}>
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about risk, VaR, beta, a ticker…"
              style={{
                flex: 1, padding: '8px 10px', border: `1px solid ${T.border}`, borderRadius: 7,
                fontSize: 12.5, outline: 'none', fontFamily: T.sans,
              }}
            />
            <button type="submit" disabled={sending || !input.trim()} style={{
              padding: '8px 14px', background: T.accent, color: '#fff', border: 'none',
              borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer', opacity: sending ? 0.6 : 1,
            }}>Send</button>
          </form>
        </>
      )}
    </div>
  );
}
