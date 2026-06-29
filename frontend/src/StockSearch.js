import React, { useState, useCallback } from 'react';
import { api } from './auth';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer,
} from 'recharts';

const T = {
  bg: '#f4f6f9',
  surface: '#ffffff',
  surfaceAlt: '#f8f9fb',
  border: '#e2e6ec',
  borderBright: '#c7cedb',
  accent: '#0a66c2',
  green: '#0a8754',
  red: '#c4314b',
  amber: '#b3760a',
  purple: '#6741d9',
  text: '#1a2233',
  textDim: '#5b6679',
  textMuted: '#8b94a3',
  mono: "'IBM Plex Mono','SF Mono',Menlo,monospace",
  sans: "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif",
};

const riskColor = (level) => {
  if (level === 'CRITICAL' || level === 'HIGH') return T.red;
  if (level === 'MODERATE') return T.amber;
  return T.green;
};

function fmtMoney(n) {
  if (n === null || n === undefined || isNaN(n)) return '—';
  return '$' + Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function fmtBig(n) {
  if (n === null || n === undefined || isNaN(n)) return '—';
  if (n >= 1e12) return '$' + (n / 1e12).toFixed(2) + 'T';
  if (n >= 1e9) return '$' + (n / 1e9).toFixed(2) + 'B';
  if (n >= 1e6) return '$' + (n / 1e6).toFixed(2) + 'M';
  return '$' + n;
}
function fmtPct(n) {
  if (n === null || n === undefined || isNaN(n)) return '—';
  return (n > 0 ? '+' : '') + Number(n).toFixed(2) + '%';
}

export default function StockSearch({ portfolios, onStockAdded }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');

  const [selectedTicker, setSelectedTicker] = useState(null);
  const [intelligence, setIntelligence] = useState(null);
  const [history, setHistory] = useState(null);
  const [loadingIntel, setLoadingIntel] = useState(false);
  const [intelError, setIntelError] = useState('');

  const [addingToPortfolio, setAddingToPortfolio] = useState(null);
  const [addStatus, setAddStatus] = useState('');

  const runSearch = useCallback(async (e) => {
    e.preventDefault();
    if (!query.trim()) return;
    setSearching(true);
    setSearchError('');
    setResults([]);
    try {
      const res = await api.get(`/api/stocks/search?query=${encodeURIComponent(query)}`);
      setResults(res.data.matches || []);
      if ((res.data.matches || []).length === 0) {
        setSearchError('No matches found.');
      }
    } catch (err) {
      setSearchError(err.response?.data?.error || 'Search failed. Please try again.');
    } finally {
      setSearching(false);
    }
  }, [query]);

  const selectTicker = useCallback(async (symbol) => {
    setSelectedTicker(symbol);
    setIntelligence(null);
    setHistory(null);
    setIntelError('');
    setAddStatus('');
    setLoadingIntel(true);
    try {
      const [intelRes, histRes] = await Promise.all([
        api.get(`/api/stocks/${symbol}/intelligence`),
        api.get(`/api/stocks/${symbol}/history`),
      ]);
      setIntelligence(intelRes.data);
      const chartData = (histRes.data || []).slice().reverse().map((v, i) => ({ i, v }));
      setHistory(chartData);
    } catch (err) {
      setIntelError(err.response?.data?.error || 'Could not load stock intelligence. Please try again.');
    } finally {
      setLoadingIntel(false);
    }
  }, []);

  const addToPortfolio = async (portfolioId) => {
    if (!intelligence) return;
    setAddingToPortfolio(portfolioId);
    setAddStatus('');
    try {
      await api.post(`/api/portfolios/${portfolioId}/stocks`, {
        ticker: intelligence.symbol,
        currentPrice: intelligence.quote.price,
        purchasePrice: intelligence.quote.price,
        quantity: 1,
        sector: intelligence.overview?.sector || 'Unknown',
      });
      setAddStatus('Added successfully.');
      if (onStockAdded) onStockAdded();
    } catch (err) {
      setAddStatus(err.response?.data?.message || err.response?.data?.error || 'Failed to add stock.');
    } finally {
      setAddingToPortfolio(null);
    }
  };

  return (
    <div style={{ marginBottom: 22 }}>
      <div style={{
        background: T.surface, border: `1px solid ${T.border}`, borderRadius: 10,
        padding: 18, boxShadow: '0 1px 3px rgba(20,30,50,0.04)',
      }}>
        <div style={{
          fontSize: 11, fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase',
          color: T.textDim, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <span>🔎</span> Stock Intelligence Search
        </div>

        <form onSubmit={runSearch} style={{ display: 'flex', gap: 8 }}>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search any ticker or company (e.g. AAPL, Tesla, Microsoft)…"
            style={{
              flex: 1, padding: '10px 14px', border: `1px solid ${T.border}`, borderRadius: 7,
              fontSize: 13.5, fontFamily: T.sans, color: T.text, outline: 'none',
            }}
          />
          <button type="submit" disabled={searching} style={{
            padding: '10px 20px', background: T.accent, color: '#fff', border: 'none',
            borderRadius: 7, fontSize: 13, fontWeight: 600, cursor: searching ? 'default' : 'pointer',
            opacity: searching ? 0.7 : 1, fontFamily: T.sans,
          }}>{searching ? 'Searching…' : 'Search'}</button>
        </form>

        {searchError && (
          <div style={{ marginTop: 10, fontSize: 12.5, color: T.red }}>{searchError}</div>
        )}

        {results.length > 0 && (
          <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 220, overflowY: 'auto' }}>
            {results.map((r) => (
              <div key={r.symbol} onClick={() => selectTicker(r.symbol)} style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                padding: '9px 12px', background: T.surfaceAlt, borderRadius: 6,
                cursor: 'pointer', border: `1px solid ${selectedTicker === r.symbol ? T.accent : 'transparent'}`,
              }}>
                <div>
                  <span style={{ fontWeight: 700, color: T.accent, fontFamily: T.mono, marginRight: 8 }}>{r.symbol}</span>
                  <span style={{ fontSize: 12.5, color: T.text }}>{r.name}</span>
                </div>
                <span style={{ fontSize: 11, color: T.textMuted }}>{r.region}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {selectedTicker && (
        <div style={{
          marginTop: 14, background: T.surface, border: `1px solid ${T.border}`, borderRadius: 10,
          padding: 20, boxShadow: '0 1px 3px rgba(20,30,50,0.04)',
        }}>
          {loadingIntel && (
            <div style={{ color: T.textMuted, fontSize: 13, padding: '20px 0', textAlign: 'center' }}>
              Loading intelligence for {selectedTicker}… (Alpha Vantage is rate-limited to 1 req/sec, this can take a couple seconds)
            </div>
          )}

          {intelError && !loadingIntel && (
            <div style={{ color: T.red, fontSize: 13, padding: '12px 0' }}>{intelError}</div>
          )}

          {intelligence && !loadingIntel && (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 20, fontWeight: 700, color: T.text }}>
                    {intelligence.symbol} <span style={{ fontSize: 14, color: T.textDim, fontWeight: 500 }}>{intelligence.overview?.name}</span>
                  </div>
                  <div style={{ fontSize: 12, color: T.textMuted, marginTop: 2 }}>
                    {intelligence.overview?.sector} · {intelligence.overview?.industry}
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: 24, fontWeight: 700, fontFamily: T.mono, color: T.text }}>
                    {fmtMoney(intelligence.quote.price)}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: intelligence.quote.change >= 0 ? T.green : T.red }}>
                    {fmtPct(intelligence.quote.changePercent)}
                  </div>
                </div>
              </div>

              {history && history.length > 1 && (
                <ResponsiveContainer width="100%" height={140}>
                  <AreaChart data={history}>
                    <defs>
                      <linearGradient id="ssArea" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={T.accent} stopOpacity={0.3} />
                        <stop offset="100%" stopColor={T.accent} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid stroke={T.border} strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="i" hide />
                    <YAxis hide domain={['auto', 'auto']} />
                    <Tooltip
                      contentStyle={{ background: T.surface, border: `1px solid ${T.border}`, borderRadius: 6, fontSize: 12 }}
                      formatter={(v) => [fmtMoney(v), 'Close']}
                      labelFormatter={() => ''}
                    />
                    <Area type="monotone" dataKey="v" stroke={T.accent} strokeWidth={2} fill="url(#ssArea)" isAnimationActive={false} />
                  </AreaChart>
                </ResponsiveContainer>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: 10, margin: '16px 0' }}>
                <Metric label="Market Cap" value={fmtBig(intelligence.overview?.marketCap)} />
                <Metric label="P/E Ratio" value={intelligence.overview?.peRatio ?? '—'} />
                <Metric label="EPS" value={intelligence.overview?.eps ?? '—'} />
                <Metric label="Beta" value={intelligence.overview?.beta ?? '—'} />
                <Metric label="52W High" value={fmtMoney(intelligence.overview?.week52High)} />
                <Metric label="52W Low" value={fmtMoney(intelligence.overview?.week52Low)} />
              </div>

              <div style={{
                padding: '12px 14px', borderRadius: 8, background: T.surfaceAlt,
                border: `1px solid ${riskColor(intelligence.riskLevel)}30`, marginBottom: 14,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8, flexWrap: 'wrap' }}>
                  <span style={{
                    fontSize: 10, fontWeight: 700, padding: '3px 9px', borderRadius: 4,
                    background: riskColor(intelligence.riskLevel) + '15', color: riskColor(intelligence.riskLevel),
                    letterSpacing: '0.05em', textTransform: 'uppercase',
                  }}>{intelligence.riskLevel} RISK</span>
                  <span style={{ fontSize: 11.5, color: T.textDim }}>Trend: <b>{intelligence.trend}</b></span>
                  {intelligence.sentimentLabel && (
                    <span style={{ fontSize: 11.5, color: T.textDim }}>
                      Sentiment: <b style={{
                        color: intelligence.sentimentLabel === 'POSITIVE' ? T.green
                              : intelligence.sentimentLabel === 'NEGATIVE' ? T.red : T.textMuted,
                      }}>{intelligence.sentimentLabel}</b>
                    </span>
                  )}
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {intelligence.signals.map((s, i) => (
                    <span key={i} style={{
                      fontSize: 11, padding: '4px 10px', borderRadius: 5,
                      background: T.purple + '12', color: T.purple, fontWeight: 600,
                    }}>{s}</span>
                  ))}
                </div>
              </div>

              <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ fontSize: 11.5, color: T.textMuted }}>Add to:</span>
                {portfolios.map((p) => (
                  <button key={p.id} onClick={() => addToPortfolio(p.id)} disabled={addingToPortfolio === p.id} style={{
                    padding: '7px 14px', fontSize: 12, fontWeight: 600, borderRadius: 6,
                    background: 'transparent', border: `1px solid ${T.accent}50`, color: T.accent,
                    cursor: addingToPortfolio === p.id ? 'default' : 'pointer', fontFamily: T.sans,
                  }}>{addingToPortfolio === p.id ? 'Adding…' : p.name}</button>
                ))}
                {portfolios.length === 0 && (
                  <span style={{ fontSize: 11.5, color: T.textMuted }}>Create a portfolio first to add stocks.</span>
                )}
              </div>
              {addStatus && (
                <div style={{ marginTop: 8, fontSize: 12, color: addStatus.includes('success') ? T.green : T.red }}>
                  {addStatus}
                </div>
              )}

              <div style={{ marginTop: 14, fontSize: 10.5, color: T.textMuted, fontStyle: 'italic' }}>
                {intelligence.disclaimer}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

function Metric({ label, value }) {
  return (
    <div style={{ background: T.surfaceAlt, borderRadius: 7, padding: '10px 12px' }}>
      <div style={{ fontSize: 9.5, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', color: T.textMuted, marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 14, fontWeight: 700, color: T.text, fontFamily: T.mono }}>{value}</div>
    </div>
  );
}
