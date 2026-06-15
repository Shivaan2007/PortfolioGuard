import React, { useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';

const API_BASE = 'http://localhost:8080';

const theme = {
    bg: '#0a0e1a',
    surface: '#111827',
    surfaceAlt: '#1a2236',
    border: '#1e2d45',
    accent: '#00d4ff',
    accentGreen: '#00e5a0',
    accentRed: '#ff4d6d',
    accentYellow: '#ffd166',
    text: '#e2e8f0',
    textMuted: '#64748b',
    textDim: '#94a3b8',
};

const styles = {
    app: { background: theme.bg, minHeight: '100vh', fontFamily: "'IBM Plex Mono', 'Courier New', monospace", color: theme.text },
    header: { background: theme.surface, borderBottom: `1px solid ${theme.border}`, padding: '16px 32px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'sticky', top: 0, zIndex: 100 },
    logo: { fontSize: '20px', fontWeight: '700', color: theme.accent, letterSpacing: '2px', textTransform: 'uppercase' },
    logoSub: { fontSize: '11px', color: theme.textMuted, letterSpacing: '3px', marginTop: '2px' },
    main: { maxWidth: '1400px', margin: '0 auto', padding: '32px' },
    grid4: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' },
    grid2: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' },
    card: { background: theme.surface, border: `1px solid ${theme.border}`, borderRadius: '4px', padding: '20px' },
    cardTitle: { fontSize: '11px', letterSpacing: '3px', textTransform: 'uppercase', color: theme.textMuted, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' },
    table: { width: '100%', borderCollapse: 'collapse', fontSize: '13px' },
    th: { textAlign: 'left', padding: '8px 12px', fontSize: '10px', letterSpacing: '2px', textTransform: 'uppercase', color: theme.textMuted, borderBottom: `1px solid ${theme.border}` },
    td: { padding: '12px', borderBottom: `1px solid ${theme.border}`, color: theme.text },
    portfolioTab: (active) => ({ padding: '8px 20px', background: active ? theme.accent : 'transparent', color: active ? theme.bg : theme.textDim, border: `1px solid ${active ? theme.accent : theme.border}`, borderRadius: '2px', cursor: 'pointer', fontSize: '12px', letterSpacing: '1px', fontFamily: 'inherit', transition: 'all 0.2s' }),
    dot: (color) => ({ width: '6px', height: '6px', borderRadius: '50%', background: color, display: 'inline-block', boxShadow: `0 0 6px ${color}` }),
    badge: (color) => ({ display: 'inline-block', padding: '2px 8px', fontSize: '10px', letterSpacing: '1px', borderRadius: '2px', background: color + '22', color: color, border: `1px solid ${color}44`, textTransform: 'uppercase' }),
    alertItem: (severity) => ({ padding: '12px 16px', marginBottom: '8px', borderLeft: `3px solid ${severity === 'HIGH' ? theme.accentRed : theme.accentYellow}`, background: theme.surfaceAlt, borderRadius: '0 4px 4px 0', fontSize: '13px' }),
    refreshBtn: { padding: '6px 14px', background: 'transparent', border: `1px solid ${theme.accent}`, color: theme.accent, borderRadius: '2px', cursor: 'pointer', fontSize: '11px', letterSpacing: '2px', fontFamily: 'inherit', textTransform: 'uppercase' },
};

// Animated number that flashes green/red on change
function LiveNumber({ value, prefix = '', suffix = '', decimals = 2, positiveColor, negativeColor }) {
    const [flash, setFlash] = useState(null);
    const prevValue = useRef(value);

    useEffect(() => {
        if (prevValue.current !== value) {
            const dir = value > prevValue.current ? 'up' : 'down';
            setFlash(dir);
            prevValue.current = value;
            const t = setTimeout(() => setFlash(null), 600);
            return () => clearTimeout(t);
        }
    }, [value]);

    const color = flash === 'up' ? theme.accentGreen : flash === 'down' ? theme.accentRed : (positiveColor || theme.accent);
    const displayColor = value !== null && positiveColor ? (value >= 0 ? positiveColor : negativeColor) : color;

    return (
        <span style={{ color: displayColor, transition: 'color 0.3s', fontWeight: '700' }}>
      {prefix}{value !== null ? Number(value).toFixed(decimals) : '—'}{suffix}
    </span>
    );
}

export default function App() {
    const [portfolios, setPortfolios] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [analytics, setAnalytics] = useState(null);
    const [risk, setRisk] = useState(null);
    const [sentiment, setSentiment] = useState(null);
    const [alerts, setAlerts] = useState([]);
    const [connected, setConnected] = useState(false);
    const [loading, setLoading] = useState(true);
    const [livePrices, setLivePrices] = useState(null); // live tick data
    const [lastUpdate, setLastUpdate] = useState(null);
    const stompClient = useRef(null);
    const priceSubscription = useRef(null);

    useEffect(() => {
        axios.get(`${API_BASE}/api/portfolios`)
            .then(res => {
                setPortfolios(res.data);
                if (res.data.length > 0) setSelectedId(res.data[0].id);
            })
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (!selectedId) return;
        setAnalytics(null);
        setRisk(null);
        setSentiment(null);
        setLivePrices(null);

        Promise.all([
            axios.get(`${API_BASE}/api/portfolios/${selectedId}/analytics`),
            axios.get(`${API_BASE}/api/portfolios/${selectedId}/risk`).catch(() => null),
            axios.get(`${API_BASE}/api/portfolios/${selectedId}/sentiment`).catch(() => null),
        ]).then(([analyticsRes, riskRes, sentimentRes]) => {
            setAnalytics(analyticsRes.data);
            if (riskRes) setRisk(riskRes.data);
            if (sentimentRes) setSentiment(sentimentRes.data);
        });
    }, [selectedId]);

    // WebSocket — alerts + live prices
    useEffect(() => {
        if (!selectedId) return;

        const client = new Client({
            webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
            onConnect: () => {
                setConnected(true);

                // Subscribe to risk alerts
                client.subscribe(`/topic/alerts/${selectedId}`, (msg) => {
                    const alert = JSON.parse(msg.body);
                    setAlerts(prev => [alert, ...prev].slice(0, 20));
                });

                // Subscribe to live price ticks
                if (priceSubscription.current) priceSubscription.current.unsubscribe();
                priceSubscription.current = client.subscribe(`/topic/prices/${selectedId}`, (msg) => {
                    const data = JSON.parse(msg.body);
                    setLivePrices(data);
                    setLastUpdate(new Date().toLocaleTimeString());
                });
            },
            onDisconnect: () => setConnected(false),
            reconnectDelay: 3000,
        });

        client.activate();
        stompClient.current = client;
        return () => {
            client.deactivate();
            setConnected(false);
        };
    }, [selectedId]);

    const selectedPortfolio = portfolios.find(p => p.id === selectedId);

    // Use live prices if available, otherwise fall back to analytics
    const displayValue = livePrices?.totalValue ?? analytics?.portfolioValue;
    const displayPnl = livePrices?.totalPnl ?? analytics?.pnl;
    const displayReturn = livePrices?.totalReturn ?? analytics?.totalReturn;

    const handleRefresh = async () => {
        if (!selectedId) return;
        try {
            await axios.post(`${API_BASE}/api/portfolios/${selectedId}/refresh-prices`);
            const res = await axios.get(`${API_BASE}/api/portfolios/${selectedId}/analytics`);
            setAnalytics(res.data);
        } catch (err) {
            console.error('Refresh failed:', err);
        }
    };

    if (loading) return (
        <div style={{ ...styles.app, display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh' }}>
            <div style={{ color: theme.accent, letterSpacing: '4px', fontSize: '14px' }}>LOADING PORTFOLIOGUARD...</div>
        </div>
    );

    return (
        <div style={styles.app}>
            {/* Header */}
            <div style={styles.header}>
                <div>
                    <div style={styles.logo}>PortfolioGuard</div>
                    <div style={styles.logoSub}>INSTITUTIONAL RISK MONITOR</div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        {portfolios.map(p => (
                            <button key={p.id} style={styles.portfolioTab(p.id === selectedId)} onClick={() => setSelectedId(p.id)}>
                                {p.name}
                            </button>
                        ))}
                    </div>
                    <div style={{ textAlign: 'right' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px' }}>
                            <span style={styles.dot(connected ? theme.accentGreen : theme.accentRed)} />
                            <span style={{ color: connected ? theme.accentGreen : theme.accentRed }}>
                {connected ? 'LIVE' : 'DISCONNECTED'}
              </span>
                        </div>
                        {lastUpdate && <div style={{ fontSize: '10px', color: theme.textMuted, marginTop: '2px' }}>Updated {lastUpdate}</div>}
                    </div>
                </div>
            </div>

            <div style={styles.main}>
                {/* Portfolio Info Bar */}
                {selectedPortfolio && (
                    <div style={{ marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
                        <div>
                            <div style={{ fontSize: '24px', fontWeight: '700', color: theme.text }}>{selectedPortfolio.name}</div>
                            <div style={{ fontSize: '12px', color: theme.textMuted, marginTop: '4px', letterSpacing: '1px' }}>
                                {selectedPortfolio.strategy} · {selectedPortfolio.stocks.length} positions · {selectedPortfolio.description}
                            </div>
                        </div>
                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button style={styles.refreshBtn} onClick={handleRefresh}>⟳ Sync Prices</button>
                            <a href={`${API_BASE}/api/portfolios/${selectedId}/report`}
                               style={{ ...styles.refreshBtn, border: `1px solid ${theme.accentGreen}`, color: theme.accentGreen, textDecoration: 'none', display: 'inline-flex', alignItems: 'center' }}
                               download>↓ PDF Report</a>
                        </div>
                    </div>
                )}

                {/* Key Metrics — live updating */}
                <div style={styles.grid4}>
                    <MetricCard label="Portfolio Value" color={theme.accent}>
                        <LiveNumber value={displayValue} prefix="$" />
                    </MetricCard>
                    <MetricCard label="Daily P&L" color={displayPnl >= 0 ? theme.accentGreen : theme.accentRed}>
                        <LiveNumber value={displayPnl} prefix={displayPnl >= 0 ? '+$' : '-$'} positiveColor={theme.accentGreen} negativeColor={theme.accentRed} />
                    </MetricCard>
                    <MetricCard label="Total Return" color={displayReturn >= 0 ? theme.accentGreen : theme.accentRed}>
                        <LiveNumber value={displayReturn} suffix="%" positiveColor={theme.accentGreen} negativeColor={theme.accentRed} />
                    </MetricCard>
                    <MetricCard label="Sharpe Ratio" color={analytics?.sharpeRatio > 1 ? theme.accentGreen : theme.accentYellow}>
                        <LiveNumber value={analytics?.sharpeRatio} decimals={3} />
                    </MetricCard>
                </div>

                {/* Risk Metrics */}
                {risk && (
                    <div style={{ ...styles.grid4, marginBottom: '24px' }}>
                        <MetricCard label="VaR 95%" color={theme.accentRed} small>
                            <LiveNumber value={risk.var95} suffix="%" />
                        </MetricCard>
                        <MetricCard label="VaR 99%" color={theme.accentRed} small>
                            <LiveNumber value={risk.var99} suffix="%" />
                        </MetricCard>
                        <MetricCard label="Portfolio Beta" color={theme.accentYellow} small>
                            <LiveNumber value={risk.beta} decimals={3} />
                        </MetricCard>
                        <MetricCard label="Positions" color={theme.textDim} small>
                            <span style={{ fontSize: '22px', fontWeight: '700', color: theme.textDim }}>{risk.stockCount ?? '—'}</span>
                        </MetricCard>
                    </div>
                )}

                {/* Positions Table + Sentiment */}
                <div style={styles.grid2}>
                    {/* Live Positions */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>
                            <span style={styles.dot(theme.accent)} /> Positions
                            {livePrices && <span style={{ ...styles.badge(theme.accentGreen), marginLeft: 'auto' }}>● LIVE</span>}
                        </div>
                        {selectedPortfolio?.stocks?.length > 0 ? (
                            <table style={styles.table}>
                                <thead>
                                <tr>
                                    {['Ticker', 'Qty', 'Purchase', 'Current', 'Change', 'P&L'].map(h => (
                                        <th key={h} style={styles.th}>{h}</th>
                                    ))}
                                </tr>
                                </thead>
                                <tbody>
                                {selectedPortfolio.stocks.map(stock => {
                                    const live = livePrices?.positions?.[stock.ticker];
                                    const currentPrice = live?.price ?? stock.currentPrice;
                                    const pnl = live?.pnl ?? (stock.currentPrice - stock.purchasePrice) * stock.quantity;
                                    const pnlPct = live?.pnlPct ?? ((stock.currentPrice - stock.purchasePrice) / stock.purchasePrice * 100);
                                    const change = live?.change ?? 0;
                                    const changePct = live?.changePct ?? 0;
                                    const isUp = change >= 0;

                                    return (
                                        <tr key={stock.id}>
                                            <td style={{ ...styles.td, color: theme.accent, fontWeight: '700' }}>{stock.ticker}</td>
                                            <td style={styles.td}>{stock.quantity}</td>
                                            <td style={styles.td}>${stock.purchasePrice.toFixed(2)}</td>
                                            <td style={styles.td}>
                                                <LiveNumber value={currentPrice} prefix="$" />
                                            </td>
                                            <td style={{ ...styles.td, color: isUp ? theme.accentGreen : theme.accentRed, fontSize: '12px' }}>
                                                {isUp ? '+' : ''}{change.toFixed(2)} ({isUp ? '+' : ''}{changePct.toFixed(2)}%)
                                            </td>
                                            <td style={{ ...styles.td, color: pnl >= 0 ? theme.accentGreen : theme.accentRed, fontWeight: '600' }}>
                                                {pnl >= 0 ? '+' : ''}{pnl.toFixed(2)} ({pnlPct.toFixed(1)}%)
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                        ) : (
                            <div style={{ color: theme.textMuted, fontSize: '13px' }}>No positions.</div>
                        )}
                    </div>

                    {/* Sentiment */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>
                            <span style={styles.dot(theme.accentYellow)} /> News Sentiment
                        </div>
                        {sentiment ? (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                {Object.entries(sentiment).map(([ticker, data]) => (
                                    <div key={ticker} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px', background: theme.surfaceAlt, borderRadius: '4px' }}>
                                        <div>
                                            <div style={{ fontWeight: '700', color: theme.accent, fontSize: '15px' }}>{ticker}</div>
                                            <div style={{ fontSize: '11px', color: theme.textMuted, marginTop: '2px' }}>Score: {data.score?.toFixed(4)}</div>
                                        </div>
                                        <span style={styles.badge(
                                            data.label === 'POSITIVE' ? theme.accentGreen :
                                                data.label === 'NEGATIVE' ? theme.accentRed : theme.textMuted
                                        )}>{data.label}</span>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div style={{ color: theme.textMuted, fontSize: '13px' }}>Loading sentiment...</div>
                        )}
                    </div>
                </div>

                {/* Live Alerts */}
                <div style={styles.card}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                        <div style={styles.cardTitle}>
                            <span style={styles.dot(theme.accentRed)} /> Live Risk Alerts
                            {alerts.length > 0 && <span style={styles.badge(theme.accentRed)}>{alerts.length}</span>}
                        </div>
                        {alerts.length > 0 && (
                            <button onClick={() => setAlerts([])} style={{ ...styles.refreshBtn, fontSize: '10px', padding: '4px 10px' }}>
                                Clear
                            </button>
                        )}
                    </div>
                    {alerts.length === 0 ? (
                        <div style={{ color: theme.textMuted, fontSize: '13px' }}>✓ No anomalies — portfolio behavior is normal</div>
                    ) : (
                        alerts.map((alert, i) => (
                            <div key={i} style={styles.alertItem(alert.severity)}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                    <div>
                    <span style={{ color: alert.severity === 'HIGH' ? theme.accentRed : theme.accentYellow, fontWeight: '700', marginRight: '8px' }}>
                      {alert.type}
                    </span>
                                        <span style={{ color: theme.textDim }}>{alert.message}</span>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
                                        <span style={{ fontSize: '10px', color: theme.textMuted }}>{alert.timestamp?.slice(11, 19)}</span>
                                        <span style={styles.badge(alert.severity === 'HIGH' ? theme.accentRed : theme.accentYellow)}>{alert.severity}</span>
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}

function MetricCard({ label, color, children, small }) {
    return (
        <div style={{ background: theme.surface, border: `1px solid ${theme.border}`, borderTop: `2px solid ${color}`, borderRadius: '4px', padding: small ? '16px' : '20px' }}>
            <div style={{ fontSize: '10px', letterSpacing: '2px', textTransform: 'uppercase', color: theme.textMuted, marginBottom: '8px' }}>{label}</div>
            <div style={{ fontSize: small ? '22px' : '28px', letterSpacing: '-1px' }}>{children}</div>
        </div>
    );
}