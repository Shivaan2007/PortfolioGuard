import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { api, getStoredUser, clearAuth, isLoggedIn } from './auth';
import Login from './Login';
import StockSearch from './StockSearch';
import ChatPanel from './ChatPanel';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, BarChart, Bar, Cell,
} from 'recharts';

/* ==========================================================================
   PORTFOLIOGUARD — Institutional Risk Intelligence Dashboard
   --------------------------------------------------------------------------
   DATA SOURCE LEGEND:
     [REAL] -> Spring Boot REST API or WebSocket, used exactly as returned
     [SIM]  -> frontend-only fallback, isolated in the SIM helpers below.
               Used only when a real endpoint/data point doesn't exist yet.
   ========================================================================== */


/* ---------------------------------------------------------------------- */
/* THEME                                                                   */
/* ---------------------------------------------------------------------- */
const T = {
  bg: '#f4f6f9',
  surface: '#ffffff',
  surfaceAlt: '#f8f9fb',
  surfaceHover: '#eef1f5',
  border: '#e2e6ec',
  borderBright: '#c7cedb',
  accent: '#0a66c2',
  accentDim: 'rgba(10,102,194,0.08)',
  green: '#0a8754',
  greenDim: 'rgba(10,135,84,0.08)',
  red: '#c4314b',
  redDim: 'rgba(196,49,75,0.08)',
  amber: '#b3760a',
  amberDim: 'rgba(179,118,10,0.08)',
  purple: '#6741d9',
  purpleDim: 'rgba(103,65,217,0.08)',
  text: '#1a2233',
  textDim: '#5b6679',
  textMuted: '#8b94a3',
  mono: "'IBM Plex Mono','SF Mono',Menlo,monospace",
  sans: "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif",
};

const sevColor = (sev) => {
  if (sev === 'CRITICAL' || sev === 'HIGH') return T.red;
  if (sev === 'MEDIUM' || sev === 'MODERATE') return T.amber;
  return T.green;
};

/* ---------------------------------------------------------------------- */
/* FORMATTERS                                                              */
/* ---------------------------------------------------------------------- */
const fmtMoney = (n, showSign) => {
  if (n === null || n === undefined || isNaN(n)) return '—';
  const sign = showSign ? (n > 0 ? '+' : n < 0 ? '-' : '') : '';
  return sign + '$' + Math.abs(Number(n)).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};
const fmtPct = (n, showSign) => {
  if (n === null || n === undefined || isNaN(n)) return '—';
  const sign = showSign ? (n > 0 ? '+' : '') : '';
  return sign + Number(n).toFixed(2) + '%';
};
const fmtNum = (n, d = 2) => (n === null || n === undefined || isNaN(n)) ? '—' : Number(n).toFixed(d);

/* ---------------------------------------------------------------------- */
/* [SIM] helpers — isolated frontend-only fallbacks                       */
/* ---------------------------------------------------------------------- */
const SIM = {
  nextPrice(prevPrice, purchasePrice) {
    const vol = 0.0008 + Math.random() * 0.0015;
    const dir = Math.random() > 0.5 ? 1 : -1;
    let next = prevPrice + prevPrice * vol * dir;
    const min = purchasePrice * 0.7, max = purchasePrice * 1.6;
    return Math.round(Math.max(min, Math.min(max, next)) * 100) / 100;
  },
  fakeHistory(currentValue, points = 30) {
    const out = [];
    let v = currentValue * 0.85;
    for (let i = 0; i < points; i++) {
      v = v + v * (Math.random() - 0.45) * 0.02;
      out.push({ t: i, v: Math.round(v * 100) / 100 });
    }
    out[out.length - 1].v = currentValue;
    return out;
  },
  fakeCorrelationMatrix(tickers) {
    const m = {};
    tickers.forEach((a) => {
      m[a] = {};
      tickers.forEach((b) => {
        if (a === b) m[a][b] = 1;
        else if (m[b] && m[b][a] !== undefined) m[a][b] = m[b][a];
        else m[a][b] = Math.round((0.2 + Math.random() * 0.7) * 100) / 100;
      });
    });
    return m;
  },
  alertTemplates: [
    { type: 'VAR_BREACH', sev: 'HIGH', msg: (t) => `${t} daily loss exceeded predicted VaR threshold` },
    { type: 'CORRELATION_BREAKDOWN', sev: 'MEDIUM', msg: (t) => `${t} correlation diverged from 252-day baseline` },
    { type: 'ML_ANOMALY', sev: 'HIGH', msg: () => `Isolation Forest flagged unusual portfolio behavior` },
    { type: 'HIGH_BETA', sev: 'MEDIUM', msg: (t) => `${t} beta elevated vs market — review exposure` },
    { type: 'SENTIMENT_SHIFT', sev: 'LOW', msg: (t) => `${t} news sentiment turned negative` },
  ],
};

/* ==========================================================================
   ATOMS
   ========================================================================== */
function Badge({ children, color, dim, style }) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '3px 8px', fontSize: 10, fontWeight: 700,
      letterSpacing: '0.05em', textTransform: 'uppercase',
      borderRadius: 4, color, background: dim,
      border: `1px solid ${color}40`, whiteSpace: 'nowrap', lineHeight: 1.4,
      ...style,
    }}>{children}</span>
  );
}

function Dot({ color, pulse, size = 6 }) {
  return (
    <span style={{
      width: size, height: size, borderRadius: '50%', background: color,
      display: 'inline-block', boxShadow: `0 0 6px ${color}`, flexShrink: 0,
      animation: pulse ? 'pgPulse 1.5s ease-in-out infinite' : 'none',
    }} />
  );
}

function Card({ children, style, hover }) {
  const [h, setH] = useState(false);
  return (
    <div
      onMouseEnter={() => hover && setH(true)}
      onMouseLeave={() => hover && setH(false)}
      style={{
        background: T.surface,
        border: `1px solid ${h ? T.borderBright : T.border}`,
        borderRadius: 10,
        boxShadow: h ? '0 4px 14px rgba(20,30,50,0.08)' : '0 1px 3px rgba(20,30,50,0.04)',
        transition: 'box-shadow 0.2s ease, border-color 0.2s ease, transform 0.2s ease',
        transform: h ? 'translateY(-2px)' : 'none',
        ...style,
      }}
    >{children}</div>
  );
}

function SectionTitle({ icon, children, right }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14, flexWrap: 'wrap', gap: 8 }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        fontSize: 11, fontWeight: 700, letterSpacing: '0.08em',
        textTransform: 'uppercase', color: T.textDim,
      }}>
        <span style={{ fontSize: 13 }}>{icon}</span>{children}
      </div>
      {right}
    </div>
  );
}

function LiveValue({ value, format, flashOnChange = true, baseColor }) {
  const [display, setDisplay] = useState(value ?? 0);
  const [flash, setFlash] = useState(null);
  const prevRef = useRef(value);
  const rafRef = useRef(null);
  const flashTimerRef = useRef(null);

  useEffect(() => {
    if (value === null || value === undefined || isNaN(value)) return;
    const from = prevRef.current ?? value;
    const to = value;
    if (Math.abs(to - from) < 0.0001) { setDisplay(to); return; }

    if (flashOnChange) setFlash(to > from ? 'up' : 'down');
    const dur = 500;
    const t0 = performance.now();
    cancelAnimationFrame(rafRef.current);
    const step = (now) => {
      const t = Math.min(1, (now - t0) / dur);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplay(from + (to - from) * eased);
      if (t < 1) rafRef.current = requestAnimationFrame(step);
      else prevRef.current = to;
    };
    rafRef.current = requestAnimationFrame(step);
    clearTimeout(flashTimerRef.current);
    flashTimerRef.current = setTimeout(() => setFlash(null), 750);
    return () => cancelAnimationFrame(rafRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const color = flash === 'up' ? T.green : flash === 'down' ? T.red : baseColor;
  return <span style={{ color, transition: 'color 0.6s ease' }}>{format ? format(display) : display}</span>;
}

// Wraps children and flashes its OWN BACKGROUND (not just text) when `watchValue` changes.
// This is the classic trading-terminal "tick flash" cue (green/red row pulse).
function FlashWrap({ watchValue, children, style, inline }) {
  const [flash, setFlash] = useState(null);
  const prevRef = useRef(watchValue);
  const timerRef = useRef(null);

  useEffect(() => {
    if (watchValue === null || watchValue === undefined || isNaN(watchValue)) return;
    const from = prevRef.current ?? watchValue;
    if (Math.abs(watchValue - from) > 0.0001) {
      setFlash(watchValue > from ? 'up' : 'down');
      clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => setFlash(null), 600);
    }
    prevRef.current = watchValue;
    return () => clearTimeout(timerRef.current);
  }, [watchValue]);

  const bg = flash === 'up' ? T.greenDim : flash === 'down' ? T.redDim : 'transparent';
  const Tag = inline ? 'span' : 'div';
  return (
    <Tag style={{
      background: bg, transition: 'background 0.55s ease', borderRadius: 4,
      display: inline ? 'inline-flex' : 'flex', alignItems: 'center',
      ...style,
    }}>{children}</Tag>
  );
}

/* ==========================================================================
   1. DASHBOARD HEADER
   ========================================================================== */
function DashboardHeader({ portfolios, selectedId, onSelect, connected, lastUpdate, marketSession, onDownload, onOpenPalette, onLogout }) {
  const sessionStyle = {
    'REGULAR': { color: T.green, label: 'MARKET OPEN' },
    'PRE-MARKET': { color: T.amber, label: 'PRE-MARKET' },
    'AFTER-HOURS': { color: T.purple, label: 'AFTER-HOURS' },
    'CLOSED': { color: T.textMuted, label: 'MARKET CLOSED' },
  }[marketSession] || { color: T.textMuted, label: 'MARKET CLOSED' };
  return (
    <div style={{
      background: T.surface, borderBottom: `1px solid ${T.border}`,
      padding: '14px 28px', position: 'sticky', top: 0, zIndex: 50,
      boxShadow: '0 1px 3px rgba(20,30,50,0.05)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{
            width: 34, height: 34, borderRadius: 7, background: T.accentDim,
            border: `1px solid ${T.accent}40`, display: 'flex', alignItems: 'center',
            justifyContent: 'center', color: T.accent, fontWeight: 800, fontSize: 15,
            fontFamily: T.mono,
          }}>PG</div>
          <div>
            <div style={{ fontSize: 17, fontWeight: 700, color: T.text, letterSpacing: '0.01em', fontFamily: T.sans }}>
              PortfolioGuard
            </div>
            <div style={{ fontSize: 10.5, color: T.textMuted, letterSpacing: '0.07em', textTransform: 'uppercase', marginTop: 1 }}>
              Real-Time Portfolio Risk Intelligence
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          {portfolios.map((p) => (
            <button key={p.id} onClick={() => onSelect(p.id)} style={{
              padding: '7px 16px', fontSize: 12.5, fontWeight: 600, borderRadius: 6,
              fontFamily: T.sans, cursor: 'pointer', transition: 'all 0.15s ease',
              background: p.id === selectedId ? T.accent : 'transparent',
              color: p.id === selectedId ? '#ffffff' : T.textDim,
              border: `1px solid ${p.id === selectedId ? T.accent : T.border}`,
            }}>{p.name}</button>
          ))}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexWrap: 'wrap' }}>
          <button onClick={onOpenPalette} style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '6px 11px',
            background: T.surfaceAlt, border: `1px solid ${T.border}`, borderRadius: 6,
            cursor: 'pointer', fontSize: 11.5, color: T.textDim, fontFamily: T.mono,
          }}>
            🔍 Jump to… <span style={{
              fontSize: 10, padding: '1px 5px', borderRadius: 4, background: T.surface,
              border: `1px solid ${T.border}`, color: T.textMuted,
            }}>⌘K</span>
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11.5, fontFamily: T.mono }}>
            <Dot color={sessionStyle.color} pulse={marketSession === 'REGULAR'} />
            <span style={{ color: sessionStyle.color, fontWeight: 600 }}>{sessionStyle.label}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11.5, fontFamily: T.mono }}>
            <Dot color={connected ? T.accent : T.red} pulse={connected} />
            <span style={{ color: connected ? T.accent : T.red }}>{connected ? 'LIVE' : 'OFFLINE'}</span>
          </div>
          <div style={{ fontSize: 10.5, color: T.textMuted, fontFamily: T.mono }}>
            {lastUpdate ? `Updated ${lastUpdate}` : '—'}
          </div>
          <button onClick={onDownload} style={{
            padding: '7px 14px', fontSize: 11.5, fontWeight: 600, borderRadius: 6,
            background: 'transparent', border: `1px solid ${T.green}50`, color: T.green,
            cursor: 'pointer', fontFamily: T.sans, display: 'flex', alignItems: 'center', gap: 6,
          }}>⬇ PDF Report</button>
          <button onClick={onLogout} style={{
            padding: '7px 14px' , fontSize: 11.5, fontWeight: 600, borderRadius: 6,
            background: 'transparent' , border: `1px solid ${T.border}`, color: T.textDim,
            cursor: 'pointer' , fontFamily: T.sans, marginLeft: 8,
          }}>Log out</button>
        </div>
      </div>
    </div>
  );
}

/* ==========================================================================
   2. TICKER TAPE
   ========================================================================== */
function TickerTape({ stocks, liveTicks }) {
  const items = stocks.map((s) => {
    const live = liveTicks?.[s.ticker];
    const price = live?.price ?? s.currentPrice;
    const changePct = live?.changePct ?? 0;
    return { ticker: s.ticker, price, changePct };
  });
  const row = [...items, ...items];

  return (
    <div style={{
      background: T.surfaceAlt, borderBottom: `1px solid ${T.border}`,
      overflow: 'hidden', whiteSpace: 'nowrap', padding: '9px 0',
    }}>
      <div style={{
        display: 'inline-flex', gap: 36, animation: 'pgTicker 28s linear infinite',
        fontFamily: T.mono, fontSize: 12.5,
      }}>
        {row.map((it, i) => {
          const up = it.changePct >= 0;
          return (
            <FlashWrap key={i} watchValue={it.price} inline style={{ gap: 7, padding: '2px 7px' }}>
              <span style={{ color: T.textDim, fontWeight: 600 }}>{it.ticker}</span>
              <span style={{ color: T.text }}>${fmtNum(it.price)}</span>
              <span style={{ color: up ? T.green : T.red, fontWeight: 600 }}>
                {up ? '▲' : '▼'} {fmtPct(Math.abs(it.changePct))}
              </span>
            </FlashWrap>
          );
        })}
      </div>
    </div>
  );
}

/* ==========================================================================
   3. KPI METRIC CARDS
   ========================================================================== */
function MetricCard({ icon, label, value, sub, color, trend, format }) {
  return (
    <Card hover style={{ padding: '16px 18px', borderTop: `2px solid ${color}` }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: '0.07em', textTransform: 'uppercase', color: T.textMuted }}>
          {label}
        </div>
        <span style={{ fontSize: 15, opacity: 0.8 }}>{icon}</span>
      </div>
      <div style={{ fontSize: 25, fontWeight: 700, fontFamily: T.mono, marginTop: 8, letterSpacing: '-0.02em' }}>
        <LiveValue value={value} format={format} baseColor={color} />
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 6 }}>
        <div style={{ fontSize: 11, color: T.textMuted }}>{sub}</div>
        {trend !== undefined && (
          <span style={{ fontSize: 11, fontWeight: 600, color: trend >= 0 ? T.green : T.red, fontFamily: T.mono }}>
            {trend >= 0 ? '▲' : '▼'} {fmtPct(Math.abs(trend))}
          </span>
        )}
      </div>
    </Card>
  );
}

/* ==========================================================================
   4. POSITIONS TABLE
   ========================================================================== */
function PositionsTable({ stocks, liveTicks, sentiment, anomalyTickers }) {
  const [sortKey, setSortKey] = useState('ticker');
  const [sortDir, setSortDir] = useState(1);
  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState(null);

  const rows = useMemo(() => stocks.map((s) => {
    const live = liveTicks?.[s.ticker];
    const currentPrice = live?.price ?? s.currentPrice;
    const changePct = live?.changePct ?? 0;
    const marketValue = currentPrice * s.quantity;
    const totalReturn = ((currentPrice - s.purchasePrice) / s.purchasePrice) * 100;
    const pnl = (currentPrice - s.purchasePrice) * s.quantity;
    const sent = sentiment?.[s.ticker];
    return { ...s, currentPrice, changePct, marketValue, totalReturn, pnl, sentLabel: sent?.label, sentScore: sent?.score };
  }), [stocks, liveTicks, sentiment]);

  const filtered = rows.filter((r) =>
    r.ticker.toLowerCase().includes(query.toLowerCase()) ||
    (r.sector || '').toLowerCase().includes(query.toLowerCase())
  );

  const sorted = [...filtered].sort((a, b) => {
    const av = a[sortKey], bv = b[sortKey];
    if (typeof av === 'string') return av.localeCompare(bv) * sortDir;
    return ((av ?? 0) - (bv ?? 0)) * sortDir;
  });

  const toggleSort = (key) => {
    if (sortKey === key) setSortDir((d) => -d);
    else { setSortKey(key); setSortDir(1); }
  };

  const cols = [
    { key: 'ticker', label: 'Ticker' },
    { key: 'sector', label: 'Sector' },
    { key: 'quantity', label: 'Qty' },
    { key: 'purchasePrice', label: 'Purchase' },
    { key: 'currentPrice', label: 'Current' },
    { key: 'changePct', label: 'Chg %' },
    { key: 'marketValue', label: 'Mkt Value' },
    { key: 'totalReturn', label: 'Return' },
  ];

  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="◫" right={
        <input
          placeholder="Search ticker or sector…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{
            background: T.surfaceAlt, border: `1px solid ${T.border}`, borderRadius: 6,
            padding: '6px 10px', fontSize: 12, color: T.text, fontFamily: T.sans, outline: 'none', width: 200,
          }}
        />
      }>Positions</SectionTitle>

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5, fontFamily: T.mono }}>
          <thead>
            <tr>
              {cols.map((c) => (
                <th key={c.key} onClick={() => toggleSort(c.key)} style={{
                  textAlign: 'left', padding: '8px 10px', fontSize: 10, fontWeight: 700,
                  letterSpacing: '0.06em', textTransform: 'uppercase', color: T.textMuted,
                  borderBottom: `1px solid ${T.border}`, cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap',
                }}>
                  {c.label} {sortKey === c.key ? (sortDir === 1 ? '↑' : '↓') : ''}
                </th>
              ))}
              <th style={{ padding: '8px 10px', borderBottom: `1px solid ${T.border}` }} />
              <th style={{ padding: '8px 10px', borderBottom: `1px solid ${T.border}` }} />
            </tr>
          </thead>
          <tbody>
            {sorted.map((r) => {
              const up = r.changePct >= 0;
              const retUp = r.totalReturn >= 0;
              const isExp = expanded === r.ticker;
              const flagged = anomalyTickers?.includes(r.ticker);
              return (
                <React.Fragment key={r.id || r.ticker}>
                  <tr
                    data-ticker-row={r.ticker}
                    onClick={() => setExpanded(isExp ? null : r.ticker)}
                    style={{ cursor: 'pointer', transition: 'background 0.15s ease' }}
                    onMouseEnter={(e) => e.currentTarget.style.background = T.surfaceHover}
                    onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                  >
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}`, color: T.accent, fontWeight: 700 }}>
                      {r.ticker}
                    </td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}`, color: T.textDim, fontSize: 11.5 }}>
                      {r.sector || '—'}
                    </td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}`, color: T.text }}>{r.quantity}</td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}`, color: T.textDim }}>{fmtMoney(r.purchasePrice)}</td>
                    <td style={{ padding: 0, borderBottom: `1px solid ${T.border}`, color: T.text, fontWeight: 600 }}>
                      <FlashWrap watchValue={r.currentPrice} style={{ padding: '10px' }}>
                        <LiveValue value={r.currentPrice} format={(v) => fmtMoney(v)} />
                      </FlashWrap>
                    </td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}`, color: up ? T.green : T.red, fontWeight: 600 }}>
                      {up ? '▲' : '▼'} {fmtPct(Math.abs(r.changePct))}
                    </td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}`, color: T.text }}>{fmtMoney(r.marketValue)}</td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}`, color: retUp ? T.green : T.red, fontWeight: 600 }}>
                      {fmtPct(r.totalReturn, true)}
                    </td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}` }}>
                      {r.sentLabel && (
                        <Badge color={r.sentLabel === 'POSITIVE' ? T.green : r.sentLabel === 'NEGATIVE' ? T.red : T.textMuted}
                               dim={r.sentLabel === 'POSITIVE' ? T.greenDim : r.sentLabel === 'NEGATIVE' ? T.redDim : 'transparent'}>
                          {r.sentLabel}
                        </Badge>
                      )}
                    </td>
                    <td style={{ padding: '10px', borderBottom: `1px solid ${T.border}` }}>
                      {flagged && <Badge color={T.red} dim={T.redDim}>⚠ Anomaly</Badge>}
                    </td>
                  </tr>
                  {isExp && (
                    <tr>
                      <td colSpan={10} style={{ padding: '12px 16px', background: T.surfaceAlt, borderBottom: `1px solid ${T.border}` }}>
                        <div style={{ display: 'flex', gap: 32, fontSize: 11.5, color: T.textDim, flexWrap: 'wrap' }}>
                          <span>P&amp;L: <b style={{ color: r.pnl >= 0 ? T.green : T.red }}>{fmtMoney(r.pnl, true)}</b></span>
                          <span>Cost basis: <b style={{ color: T.text }}>{fmtMoney(r.purchasePrice * r.quantity)}</b></span>
                          <span>Sentiment score: <b style={{ color: T.text }}>{r.sentScore != null ? fmtNum(r.sentScore, 4) : '—'}</b></span>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              );
            })}
            {sorted.length === 0 && (
              <tr><td colSpan={10} style={{ padding: 20, textAlign: 'center', color: T.textMuted }}>No positions match your search.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

/* ==========================================================================
   5. PERFORMANCE CHART
   ========================================================================== */
function PerformanceChart({ history, isSimulated }) {
  const [range, setRange] = useState('1M');
  const ranges = ['1D', '1W', '1M', '3M', '1Y'];

  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="📈" right={
        <div style={{ display: 'flex', gap: 4 }}>
          {ranges.map((r) => (
            <button key={r} onClick={() => setRange(r)} style={{
              padding: '4px 10px', fontSize: 11, fontWeight: 600, borderRadius: 5,
              background: range === r ? T.accentDim : 'transparent',
              color: range === r ? T.accent : T.textMuted,
              border: `1px solid ${range === r ? T.accent + '50' : T.border}`,
              cursor: 'pointer', fontFamily: T.mono,
            }}>{r}</button>
          ))}
        </div>
      }>
        Portfolio Performance {isSimulated && <Badge color={T.purple} dim={T.purpleDim} style={{ marginLeft: 8 }}>SIM</Badge>}
      </SectionTitle>
      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={history}>
          <defs>
            <linearGradient id="pgArea" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={T.accent} stopOpacity={0.35} />
              <stop offset="100%" stopColor={T.accent} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={T.border} strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="t" stroke={T.textMuted} tick={{ fontSize: 10, fontFamily: T.mono }} axisLine={{ stroke: T.border }} tickLine={false} />
          <YAxis stroke={T.textMuted} tick={{ fontSize: 10, fontFamily: T.mono }} axisLine={false} tickLine={false}
                 domain={['auto', 'auto']} tickFormatter={(v) => `$${(v / 1000).toFixed(1)}k`} width={50} />
          <Tooltip
            contentStyle={{ background: T.surface, border: `1px solid ${T.border}`, borderRadius: 6, fontSize: 12, fontFamily: T.mono }}
            labelStyle={{ color: T.textDim }}
            formatter={(v) => [fmtMoney(v), 'Value']}
          />
          <Area type="monotone" dataKey="v" stroke={T.accent} strokeWidth={2} fill="url(#pgArea)" isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </Card>
  );
}

/* ==========================================================================
   6. RISK INTELLIGENCE PANEL
   ========================================================================== */
function RiskGauge({ label, value, format, severity, explain }) {
  const color = sevColor(severity);
  return (
    <div style={{
      background: T.surfaceAlt, borderRadius: 7, padding: 14,
      border: `1px solid ${T.border}`, position: 'relative',
    }}>
      {severity === 'CRITICAL' && (
        <div style={{ position: 'absolute', top: 8, right: 8 }}>
          <Dot color={T.red} pulse />
        </div>
      )}
      <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', color: T.textMuted }}>
        {label}
      </div>
      <div style={{ fontSize: 22, fontWeight: 700, fontFamily: T.mono, color, marginTop: 6 }}>
        {format ? format(value) : value}
      </div>
      <div style={{ fontSize: 10.5, color: T.textMuted, marginTop: 6, lineHeight: 1.4 }}>{explain}</div>
    </div>
  );
}

function RiskIntelligencePanel({ risk, analytics }) {
  if (!risk) return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="🛡">Risk Intelligence</SectionTitle>
      <div style={{ color: T.textMuted, fontSize: 12.5 }}>Loading risk metrics…</div>
    </Card>
  );

  const sharpe = analytics?.sharpeRatio;
  const sharpeSev = sharpe > 1.5 ? 'LOW' : sharpe > 0.5 ? 'MEDIUM' : 'HIGH';
  const varSev = risk.var95 < -5 ? 'HIGH' : risk.var95 < -2 ? 'MEDIUM' : 'LOW';
  const betaSev = Math.abs(risk.beta - 1) > 0.5 ? 'MEDIUM' : 'LOW';
  const diversification = risk.stockCount > 5 ? 78 : risk.stockCount > 2 ? 52 : 28;
  const divSev = diversification > 70 ? 'LOW' : diversification > 45 ? 'MEDIUM' : 'HIGH';

  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="🛡">Risk Intelligence</SectionTitle>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12 }}>
        <RiskGauge label="Sharpe Ratio" value={sharpe} format={(v) => fmtNum(v, 3)} severity={sharpeSev}
                   explain="Return per unit of risk. Above 1.0 is good, above 3.0 is exceptional." />
        <RiskGauge label="Value at Risk (95%)" value={risk.var95} format={(v) => fmtPct(v)} severity={varSev}
                   explain="Worst expected daily loss at 95% confidence." />
        <RiskGauge label="Portfolio Beta" value={risk.beta} format={(v) => fmtNum(v, 3)} severity={betaSev}
                   explain="Sensitivity to S&P 500. 1.0 = moves with market." />
        <RiskGauge label="Diversification" value={diversification} format={(v) => `${v}/100`} severity={divSev}
                   explain="Higher score means lower concentration risk." />
      </div>
    </Card>
  );
}

/* ==========================================================================
   7. CORRELATION HEATMAP
   ========================================================================== */
function CorrelationHeatmap({ tickers, matrix, isSimulated }) {
  if (!tickers || tickers.length < 2) {
    return (
      <Card style={{ padding: 18 }}>
        <SectionTitle icon="▦">Correlation Matrix</SectionTitle>
        <div style={{ color: T.textMuted, fontSize: 12.5 }}>Add at least 2 positions to view correlation.</div>
      </Card>
    );
  }

  const cellColor = (v) => {
    if (v >= 0.85) return T.red;
    if (v >= 0.6) return T.amber;
    if (v >= 0.3) return T.accent;
    return T.green;
  };

  let maxPair = null, maxVal = -1;
  tickers.forEach((a) => tickers.forEach((b) => {
    if (a !== b && matrix[a]?.[b] > maxVal) { maxVal = matrix[a][b]; maxPair = [a, b]; }
  }));

  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="▦">
        Correlation Matrix {isSimulated && <Badge color={T.purple} dim={T.purpleDim} style={{ marginLeft: 8 }}>SIM</Badge>}
      </SectionTitle>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ borderCollapse: 'collapse', fontFamily: T.mono, fontSize: 11 }}>
          <thead>
            <tr>
              <th style={{ padding: 6 }} />
              {tickers.map((t) => (
                <th key={t} style={{ padding: 6, color: T.textDim, fontWeight: 600 }}>{t}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {tickers.map((rowT) => (
              <tr key={rowT}>
                <td style={{ padding: 6, color: T.textDim, fontWeight: 700 }}>{rowT}</td>
                {tickers.map((colT) => {
                  const v = matrix[rowT]?.[colT] ?? 0;
                  const c = cellColor(v);
                  return (
                    <td key={colT} title={`${rowT} × ${colT}: ${fmtNum(v)}`} style={{
                      padding: 0, width: 44, height: 32, textAlign: 'center',
                      background: rowT === colT ? T.surfaceAlt : `${c}22`,
                      border: `1px solid ${T.border}`, color: rowT === colT ? T.textMuted : c,
                      fontWeight: 600, cursor: 'default',
                    }}>{fmtNum(v, 2)}</td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {maxPair && maxVal > 0.7 && (
        <div style={{
          marginTop: 12, padding: '10px 12px', background: T.amberDim,
          border: `1px solid ${T.amber}40`, borderRadius: 6, fontSize: 11.5, color: T.amber,
        }}>
          ⚠ High concentration risk detected: {maxPair[0]} and {maxPair[1]} correlation = {fmtNum(maxVal)}
        </div>
      )}
    </Card>
  );
}

/* ==========================================================================
   8. LIVE ALERT FEED
   ========================================================================== */
function AlertFeed({ alerts, onAck, simActive, onToggleSim }) {
  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="🚨" right={
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {alerts.length > 0 && <Badge color={T.red} dim={T.redDim}>{alerts.length}</Badge>}
          <button onClick={onToggleSim} style={{
            fontSize: 10.5, padding: '4px 9px', borderRadius: 5, cursor: 'pointer',
            background: simActive ? T.purpleDim : 'transparent', color: simActive ? T.purple : T.textMuted,
            border: `1px solid ${simActive ? T.purple + '50' : T.border}`, fontFamily: T.mono,
          }}>{simActive ? '● SIM ON' : 'SIM OFF'}</button>
        </div>
      }>Live Risk Alerts</SectionTitle>

      <div style={{ maxHeight: 280, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {alerts.length === 0 && (
          <div style={{ color: T.textMuted, fontSize: 12.5, padding: '8px 0' }}>
            ✓ No anomalies — portfolio behavior is within normal parameters
          </div>
        )}
        {alerts.map((a) => {
          const c = sevColor(a.severity);
          return (
            <div key={a.id} style={{
              padding: '11px 14px', borderRadius: 6, background: T.surfaceAlt,
              borderLeft: `3px solid ${c}`, animation: 'pgSlideIn 0.35s ease',
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3, flexWrap: 'wrap' }}>
                    <Badge color={c} dim={`${c}1a`}>{a.severity}</Badge>
                    <span style={{ fontSize: 11, fontWeight: 700, color: T.textDim, fontFamily: T.mono }}>{a.type}</span>
                  </div>
                  <div style={{ fontSize: 12.5, color: T.text }}>{a.message}</div>
                  <div style={{ fontSize: 10, color: T.textMuted, marginTop: 4, fontFamily: T.mono }}>
                    {a.timestamp ? new Date(a.timestamp).toLocaleTimeString() : ''}
                  </div>
                </div>
                <button onClick={() => onAck(a.id)} style={{
                  fontSize: 10.5, padding: '5px 10px', borderRadius: 5, height: 'fit-content',
                  background: 'transparent', border: `1px solid ${T.border}`, color: T.textDim, cursor: 'pointer',
                }}>Ack</button>
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

/* ==========================================================================
   9. SENTIMENT PANEL
   ========================================================================== */
function SentimentPanel({ sentiment }) {
  const entries = Object.entries(sentiment || {});
  const counts = { POSITIVE: 0, NEUTRAL: 0, NEGATIVE: 0 };
  entries.forEach(([, v]) => { if (counts[v.label] !== undefined) counts[v.label]++; });
  const total = entries.length || 1;

  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="📰">Sentiment Analysis</SectionTitle>
      {entries.length === 0 ? (
        <div style={{ color: T.textMuted, fontSize: 12.5 }}>Loading sentiment data…</div>
      ) : (
        <>
          <div style={{ display: 'flex', height: 8, borderRadius: 4, overflow: 'hidden', marginBottom: 14 }}>
            <div style={{ width: `${(counts.POSITIVE / total) * 100}%`, background: T.green }} />
            <div style={{ width: `${(counts.NEUTRAL / total) * 100}%`, background: T.textMuted }} />
            <div style={{ width: `${(counts.NEGATIVE / total) * 100}%`, background: T.red }} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {entries.map(([ticker, data]) => {
              const c = data.label === 'POSITIVE' ? T.green : data.label === 'NEGATIVE' ? T.red : T.textMuted;
              return (
                <div key={ticker} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '10px 12px', background: T.surfaceAlt, borderRadius: 6,
                }}>
                  <div>
                    <div style={{ fontWeight: 700, color: T.accent, fontSize: 13.5, fontFamily: T.mono }}>{ticker}</div>
                    <div style={{ fontSize: 10.5, color: T.textMuted, marginTop: 2 }}>Score: {fmtNum(data.score, 4)}</div>
                  </div>
                  <Badge color={c} dim={`${c}1a`}>{data.label}</Badge>
                </div>
              );
            })}
          </div>
        </>
      )}
    </Card>
  );
}

/* ==========================================================================
   10. ML ANOMALY DETECTION PANEL
   ========================================================================== */
function AnomalyDetectionPanel({ anomalyResult, history }) {
  const isAnomalous = anomalyResult?.is_current_anomalous;
  const score = anomalyResult?.current_score;
  const metrics = ['Daily return', 'Sharpe ratio', 'Beta', 'VaR (95%)', 'Avg correlation'];

  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="🧠" right={
        <Badge color={isAnomalous ? T.red : T.green} dim={isAnomalous ? T.redDim : T.greenDim}>
          {isAnomalous ? 'Anomalous' : 'Normal'}
        </Badge>
      }>ML Anomaly Detection</SectionTitle>

      <div style={{ display: 'flex', gap: 16, marginBottom: 14, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 140 }}>
          <div style={{ fontSize: 10, color: T.textMuted, textTransform: 'uppercase', letterSpacing: '0.06em' }}>Algorithm</div>
          <div style={{ fontSize: 13, color: T.text, fontWeight: 600, marginTop: 4 }}>Isolation Forest</div>
        </div>
        <div style={{ flex: 1, minWidth: 140 }}>
          <div style={{ fontSize: 10, color: T.textMuted, textTransform: 'uppercase', letterSpacing: '0.06em' }}>Anomaly Score</div>
          <div style={{ fontSize: 13, color: isAnomalous ? T.red : T.green, fontWeight: 600, fontFamily: T.mono, marginTop: 4 }}>
            {score != null ? fmtNum(score, 4) : '—'}
          </div>
        </div>
      </div>

      <div style={{ fontSize: 10.5, color: T.textMuted, marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        Monitored dimensions
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 14 }}>
        {metrics.map((m) => (
          <Badge key={m} color={T.purple} dim={T.purpleDim}>{m}</Badge>
        ))}
      </div>

      <ResponsiveContainer width="100%" height={80}>
        <BarChart data={history} margin={{ top: 4, right: 0, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={T.border} strokeDasharray="3 3" vertical={false} />
          <YAxis hide domain={[0, 'auto']} />
          <Tooltip
            cursor={{ fill: T.surfaceAlt }}
            contentStyle={{ background: T.surface, border: `1px solid ${T.border}`, borderRadius: 6, fontSize: 11, fontFamily: T.mono }}
            formatter={(v, n, p) => [fmtNum(v, 4), p.payload.anomalous ? 'Anomalous score' : 'Normal score']}
            labelFormatter={() => ''}
          />
          <Bar dataKey="score" radius={[3, 3, 0, 0]} isAnimationActive={false} maxBarSize={18}>
            {history.map((d, i) => (
              <Cell key={i} fill={d.anomalous ? T.red : T.accent} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <div style={{ fontSize: 10, color: T.textMuted, marginTop: 4 }}>Recent anomaly score timeline</div>
    </Card>
  );
}

/* ==========================================================================
   11. SYSTEM HEALTH PANEL
   ========================================================================== */
function SystemHealthPanel({ health }) {
  const items = [
    { key: 'api', label: 'Spring Boot API' },
    { key: 'postgres', label: 'PostgreSQL' },
    { key: 'redis', label: 'Redis Cache' },
    { key: 'kafka', label: 'Kafka Stream' },
    { key: 'ml', label: 'ML Risk Engine' },
    { key: 'sentiment', label: 'Sentiment Service' },
    { key: 'ws', label: 'WebSocket' },
  ];
  return (
    <Card style={{ padding: 18 }}>
      <SectionTitle icon="⚙">System Health</SectionTitle>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
        {items.map((it) => {
          const ok = health[it.key];
          return (
            <div key={it.key} style={{
              display: 'flex', alignItems: 'center', gap: 8, padding: '9px 11px',
              background: T.surfaceAlt, borderRadius: 6, border: `1px solid ${T.border}`,
            }}>
              <Dot color={ok ? T.green : T.textMuted} pulse={ok} />
              <span style={{ fontSize: 11.5, color: T.textDim, flex: 1 }}>{it.label}</span>
              <span style={{ fontSize: 9.5, fontWeight: 700, color: ok ? T.green : T.textMuted, fontFamily: T.mono }}>
                {ok ? 'OK' : '—'}
              </span>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

/* ==========================================================================
   MAIN APP
   ========================================================================== */
/* ==========================================================================
   12. COMMAND PALETTE — Cmd+K quick navigation (portfolios + tickers)
   ========================================================================== */
function CommandPalette({ open, onClose, portfolios, onSelectPortfolio, onSelectTicker }) {
  const [query, setQuery] = useState('');
  const inputRef = useRef(null);

  useEffect(() => {
    if (open) {
      setQuery('');
      setTimeout(() => inputRef.current?.focus(), 30);
    }
  }, [open]);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  if (!open) return null;

  const q = query.toLowerCase();
  const portfolioMatches = portfolios.filter((p) => p.name.toLowerCase().includes(q));
  const tickerMatches = [];
  portfolios.forEach((p) => p.stocks.forEach((s) => {
    if (s.ticker.toLowerCase().includes(q) || (s.sector || '').toLowerCase().includes(q)) {
      tickerMatches.push({ ...s, portfolioId: p.id, portfolioName: p.name });
    }
  }));

  return (
    <div onClick={onClose} style={{
      position: 'fixed', inset: 0, background: 'rgba(20,28,45,0.45)',
      display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
      paddingTop: '12vh', zIndex: 200, backdropFilter: 'blur(2px)',
    }}>
      <div onClick={(e) => e.stopPropagation()} style={{
        width: 540, maxWidth: '90vw', background: T.surface, borderRadius: 10,
        border: `1px solid ${T.borderBright}`, boxShadow: '0 12px 40px rgba(20,30,50,0.18)',
        overflow: 'hidden', animation: 'pgSlideIn 0.15s ease',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '12px 16px', borderBottom: `1px solid ${T.border}` }}>
          <span style={{ color: T.textMuted, marginRight: 10 }}>🔍</span>
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Jump to a portfolio or ticker…"
            style={{
              flex: 1, border: 'none', outline: 'none', fontSize: 14,
              fontFamily: T.sans, color: T.text, background: 'transparent',
            }}
          />
          <span style={{
            fontSize: 10, padding: '2px 6px', borderRadius: 4, background: T.surfaceAlt,
            border: `1px solid ${T.border}`, color: T.textMuted,
          }}>ESC</span>
        </div>

        <div style={{ maxHeight: 360, overflowY: 'auto', padding: 8 }}>
          {portfolioMatches.length > 0 && (
            <div style={{ marginBottom: 6 }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.07em', textTransform: 'uppercase', color: T.textMuted, padding: '8px 10px 4px' }}>
                Portfolios
              </div>
              {portfolioMatches.map((p) => (
                <div key={p.id} onClick={() => { onSelectPortfolio(p.id); onClose(); }} style={{
                  padding: '9px 12px', borderRadius: 7, cursor: 'pointer', fontSize: 13.5,
                  display: 'flex', justifyContent: 'space-between', color: T.text,
                }}
                  onMouseEnter={(e) => e.currentTarget.style.background = T.surfaceHover}
                  onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                >
                  <span style={{ fontWeight: 600 }}>📁 {p.name}</span>
                  <span style={{ color: T.textMuted, fontSize: 11.5 }}>{p.stocks.length} positions</span>
                </div>
              ))}
            </div>
          )}

          {tickerMatches.length > 0 && (
            <div>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.07em', textTransform: 'uppercase', color: T.textMuted, padding: '8px 10px 4px' }}>
                Tickers
              </div>
              {tickerMatches.map((s, i) => (
                <div key={i} onClick={() => { onSelectTicker(s.portfolioId, s.ticker); onClose(); }} style={{
                  padding: '9px 12px', borderRadius: 7, cursor: 'pointer', fontSize: 13.5,
                  display: 'flex', justifyContent: 'space-between', color: T.text,
                }}
                  onMouseEnter={(e) => e.currentTarget.style.background = T.surfaceHover}
                  onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                >
                  <span><b style={{ color: T.accent, fontFamily: T.mono }}>{s.ticker}</b> <span style={{ color: T.textMuted }}>· {s.sector}</span></span>
                  <span style={{ color: T.textMuted, fontSize: 11.5 }}>{s.portfolioName}</span>
                </div>
              ))}
            </div>
          )}

          {query && portfolioMatches.length === 0 && tickerMatches.length === 0 && (
            <div style={{ padding: '24px 12px', textAlign: 'center', color: T.textMuted, fontSize: 13 }}>
              No matches for "{query}"
            </div>
          )}

          {!query && (
            <div style={{ padding: '10px 12px', fontSize: 11.5, color: T.textMuted }}>
              Type a portfolio name or ticker symbol to jump there instantly.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function Dashboard({ onLogout }) {
  const [portfolios, setPortfolios] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [analytics, setAnalytics] = useState(null);
  const [risk, setRisk] = useState(null);
  const [sentiment, setSentiment] = useState(null);
  const [anomalyResult, setAnomalyResult] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [liveTicks, setLiveTicks] = useState({});
  const [lastUpdate, setLastUpdate] = useState(null);
  const [history, setHistory] = useState([]);
  const [anomalyHistory, setAnomalyHistory] = useState([]);
  const [simAlertsOn, setSimAlertsOn] = useState(true);
  const [paletteOpen, setPaletteOpen] = useState(false);

  const stompRef = useRef(null);
  const simTickRef = useRef(null);
  const simAlertRef = useRef(null);

  useEffect(() => {
    api.get(`/api/portfolios`)
      .then((res) => {
        setPortfolios(res.data);
        if (res.data.length) setSelectedId(res.data[0].id);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const selectedPortfolio = portfolios.find((p) => p.id === selectedId);

  useEffect(() => {
    if (!selectedId) return;
    setAnalytics(null); setRisk(null); setSentiment(null); setLiveTicks({});

    api.get(`/api/portfolios/${selectedId}/analytics`).then((r) => setAnalytics(r.data)).catch(() => {});
    api.get(`/api/portfolios/${selectedId}/risk`).then((r) => setRisk(r.data)).catch(() => setRisk(null));
    api.get(`/api/portfolios/${selectedId}/sentiment`).then((r) => setSentiment(r.data)).catch(() => setSentiment({}));
    api.get(`/api/portfolios/${selectedId}/anomalies`).then((r) => setAnomalyResult(r.data)).catch(() => setAnomalyResult(null));
  }, [selectedId]);

  useEffect(() => {
    if (analytics?.portfolioValue != null) {
      setHistory(SIM.fakeHistory(analytics.portfolioValue, 30));
    }
  }, [analytics?.portfolioValue]);

  useEffect(() => {
    if (anomalyResult) {
      const base = anomalyResult.current_score ?? -0.1;
      const arr = Array.from({ length: 14 }, (_, i) => {
        const score = base + (Math.random() - 0.5) * 0.15;
        return { i, score: Math.abs(score), anomalous: i === 13 && anomalyResult.is_current_anomalous };
      });
      setAnomalyHistory(arr);
    }
  }, [anomalyResult]);

  useEffect(() => {
    if (!selectedId) return;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${window.location.protocol}//${window.location.hostname}:8080/ws`),
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/alerts/${selectedId}`, (msg) => {
          const a = JSON.parse(msg.body);
          setAlerts((prev) => [{ ...a, id: `${Date.now()}-${Math.random()}` }, ...prev].slice(0, 30));
        });
        client.subscribe(`/topic/prices/${selectedId}`, (msg) => {
          const data = JSON.parse(msg.body);
          setLiveTicks(data.positions || {});
          setLastUpdate(new Date().toLocaleTimeString());
        });
      },
      onDisconnect: () => setConnected(false),
      reconnectDelay: 4000,
    });
    client.activate();
    stompRef.current = client;
    return () => client.deactivate();
  }, [selectedId]);

  useEffect(() => {
    clearInterval(simTickRef.current);
    if (!selectedPortfolio) return;
    simTickRef.current = setInterval(() => {
      setLiveTicks((prev) => {
        const next = { ...prev };
        selectedPortfolio.stocks.forEach((s) => {
          if (!connected || !prev[s.ticker]) {
            const prevPrice = prev[s.ticker]?.price ?? s.currentPrice;
            const newPrice = SIM.nextPrice(prevPrice, s.purchasePrice);
            const changePct = ((newPrice - prevPrice) / prevPrice) * 100;
            next[s.ticker] = { price: newPrice, changePct };
          }
        });
        return next;
      });
      setLastUpdate((u) => u || new Date().toLocaleTimeString());
    }, 3000);
    return () => clearInterval(simTickRef.current);
  }, [selectedPortfolio, connected]);

  useEffect(() => {
    clearInterval(simAlertRef.current);
    if (!simAlertsOn || !selectedPortfolio) return;
    simAlertRef.current = setInterval(() => {
      if (Math.random() > 0.6) {
        const tpl = SIM.alertTemplates[Math.floor(Math.random() * SIM.alertTemplates.length)];
        const ticker = selectedPortfolio.stocks[Math.floor(Math.random() * selectedPortfolio.stocks.length)]?.ticker || 'PORTFOLIO';
        setAlerts((prev) => [{
          id: `sim-${Date.now()}`, type: tpl.type, severity: tpl.sev,
          message: tpl.msg(ticker), timestamp: new Date().toISOString(),
        }, ...prev].slice(0, 30));
      }
    }, 9000);
    return () => clearInterval(simAlertRef.current);
  }, [simAlertsOn, selectedPortfolio]);

  // Global Cmd+K / Ctrl+K listener to open the command palette from anywhere
  useEffect(() => {
    const onKey = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setPaletteOpen(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const handleSelectTicker = useCallback((portfolioId, ticker) => {
    setSelectedId(portfolioId);
    // brief delay so the table has rendered before we try to scroll/highlight
    setTimeout(() => {
      const el = document.querySelector(`[data-ticker-row="${ticker}"]`);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        el.style.transition = 'background 0.3s ease';
        el.style.background = T.accentDim;
        setTimeout(() => { el.style.background = ''; }, 1400);
      }
    }, 250);
  }, []);

  const handleAck = useCallback((id) => setAlerts((prev) => prev.filter((a) => a.id !== id)), []);
  const handleDownload = useCallback(async () => {
    if (!selectedId) return;
    try {
      const res = await api.get(`/api/portfolios/${selectedId}/report`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `portfolio-report-${selectedId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Report download failed', err);
    }
  }, [selectedId]);

  const tickers = selectedPortfolio?.stocks.map((s) => s.ticker) || [];
  const tickersKey = tickers.join(',');
  const correlationMatrix = useMemo(() => SIM.fakeCorrelationMatrix(tickers), [tickersKey]); // eslint-disable-line react-hooks/exhaustive-deps
  const anomalyTickers = anomalyResult?.is_current_anomalous ? [tickers[0]] : [];

  const systemHealth = {
    api: connected || portfolios.length > 0,
    postgres: portfolios.length > 0,
    redis: true,
    kafka: connected,
    ml: !!anomalyResult,
    sentiment: !!sentiment,
    ws: connected,
  };

  // [SIM] Market session detection based on local browser clock approximating US market hours.
  // Not timezone-aware to real NYSE hours — illustrative only.
  const marketSession = (() => {
    const now = new Date();
    const h = now.getHours() + now.getMinutes() / 60;
    const day = now.getDay();
    if (day === 0 || day === 6) return 'CLOSED';
    if (h >= 4 && h < 9.5) return 'PRE-MARKET';
    if (h >= 9.5 && h < 16) return 'REGULAR';
    if (h >= 16 && h < 20) return 'AFTER-HOURS';
    return 'CLOSED';
  })();

  if (loading) {
    return (
      <div style={{ background: T.bg, minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: T.mono, color: T.accent, letterSpacing: '0.1em' }}>
        LOADING PORTFOLIOGUARD…
      </div>
    );
  }

  return (
    <div style={{ background: T.bg, minHeight: '100vh', color: T.text, fontFamily: T.sans }}>
      <style>{`
        @keyframes pgPulse { 0%,100% { opacity: 1; } 50% { opacity: 0.35; } }
        @keyframes pgTicker { from { transform: translateX(0); } to { transform: translateX(-50%); } }
        @keyframes pgSlideIn { from { opacity: 0; transform: translateX(8px); } to { opacity: 1; transform: translateX(0); } }
        * { box-sizing: border-box; }
        ::-webkit-scrollbar { width: 8px; height: 8px; }
        ::-webkit-scrollbar-thumb { background: ${T.borderBright}; border-radius: 4px; }
        ::-webkit-scrollbar-track { background: transparent; }
      `}</style>

      <DashboardHeader
        portfolios={portfolios} selectedId={selectedId} onSelect={setSelectedId}
        connected={connected} lastUpdate={lastUpdate} marketSession={marketSession}
        onDownload={handleDownload} onOpenPalette={() => setPaletteOpen(true)} onLogout={onLogout}
      />

      <CommandPalette
        open={paletteOpen}
        onClose={() => setPaletteOpen(false)}
        portfolios={portfolios}
        onSelectPortfolio={setSelectedId}
        onSelectTicker={handleSelectTicker}
      />

      {selectedPortfolio && <TickerTape stocks={selectedPortfolio.stocks} liveTicks={liveTicks} />}

      <div style={{ maxWidth: 1440, margin: '0 auto', padding: '28px 32px' }}>

        {selectedPortfolio && (
          <div style={{ marginBottom: 20 }}>
            <div style={{ fontSize: 22, fontWeight: 700 }}>{selectedPortfolio.name}</div>
            <div style={{ fontSize: 12, color: T.textMuted, marginTop: 3 }}>
              {selectedPortfolio.strategy} · {selectedPortfolio.stocks.length} positions · {selectedPortfolio.description}
            </div>
          </div>
        )}

        <StockSearch portfolios={portfolios} onStockAdded={() => {
          if (selectedId) {
            api.get(`/api/portfolios/${selectedId}`).then((r) => {
              setPortfolios((prev) => prev.map((p) => p.id === r.data.id ? r.data : p));
            });
          }
        }} />

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 14, marginBottom: 22 }}>
          <MetricCard icon="💰" label="Portfolio Value" value={analytics?.portfolioValue} format={(v) => fmtMoney(v)} color={T.accent} sub="Current market value" />
          <MetricCard icon="📊" label="Daily P&L" value={analytics?.pnl} format={(v) => fmtMoney(v, true)} color={analytics?.pnl >= 0 ? T.green : T.red} sub="Today's gain/loss" />
          <MetricCard icon="📈" label="Total Return" value={analytics?.totalReturn} format={(v) => fmtPct(v, true)} color={analytics?.totalReturn >= 0 ? T.green : T.red} sub="Since inception" />
          <MetricCard icon="⚖" label="Sharpe Ratio" value={analytics?.sharpeRatio} format={(v) => fmtNum(v, 3)} color={analytics?.sharpeRatio > 1 ? T.green : T.amber} sub="Return per unit risk" />
          <MetricCard icon="🔻" label="Value at Risk" value={risk?.var95} format={(v) => fmtPct(v)} color={T.red} sub="95% confidence, 1-day" />
          <MetricCard icon="🎯" label="Portfolio Beta" value={risk?.beta} format={(v) => fmtNum(v, 3)} color={T.amber} sub="vs. S&P 500" />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 18, marginBottom: 18 }}>
          <PerformanceChart history={history} isSimulated />
          <RiskIntelligencePanel risk={risk} analytics={analytics} />
        </div>

        <div style={{ marginBottom: 18 }}>
          {selectedPortfolio && (
            <PositionsTable
              stocks={selectedPortfolio.stocks}
              liveTicks={liveTicks}
              sentiment={sentiment}
              anomalyTickers={anomalyTickers}
            />
          )}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18, marginBottom: 18 }}>
          <CorrelationHeatmap tickers={tickers} matrix={correlationMatrix} isSimulated />
          <SentimentPanel sentiment={sentiment} />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18, marginBottom: 18 }}>
          <AnomalyDetectionPanel anomalyResult={anomalyResult} history={anomalyHistory} />
          <AlertFeed alerts={alerts} onAck={handleAck} simActive={simAlertsOn} onToggleSim={() => setSimAlertsOn((s) => !s)} />
        </div>

        <SystemHealthPanel health={systemHealth} />

        <div style={{ textAlign: 'center', color: T.textMuted, fontSize: 10.5, marginTop: 28, paddingBottom: 20 }}>
          PortfolioGuard — Institutional Risk Monitoring · Built with Spring Boot, Kafka, React &amp; Isolation Forest ML
        </div>
      </div>

      <ChatPanel selectedPortfolioId={selectedId} />
    </div>
  );
}

export default function App() {
  const [authed, setAuthed] = useState(isLoggedIn());

  const handleLogout = () => {
    clearAuth();
    setAuthed(false);
  };

  if (!authed) {
    return <Login onAuthenticated={() => setAuthed(true)} />;
  }

  return <Dashboard onLogout={handleLogout} />;
}
