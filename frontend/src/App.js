import React, { useState, useEffect, useCallback } from 'react';
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
    app: {
        background: theme.bg,
        minHeight: '100vh',
        fontFamily: "'IBM Plex Mono', 'Courier New', monospace",
        color: theme.text,
        padding: '0',
    },
    header: {
        background: theme.surface,
        borderBottom: `1px solid ${theme.border}`,
        padding: '16px 32px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        position: 'sticky',
        top: 0,
        zIndex: 100,
    },
    logo: {
        fontSize: '20px',
        fontWeight: '700',
        color: theme.accent,
        letterSpacing: '2px',
        textTransform: 'uppercase',
    },
    logoSub: {
        fontSize: '11px',
        color: theme.textMuted,
        letterSpacing: '3px',
        marginTop: '2px',
    },
    main: {
        maxWidth: '1400px',
        margin: '0 auto',
        padding: '32px',
    },
    grid4: {
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: '16px',
        marginBottom: '24px',
    },
    grid2: {
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: '24px',
        marginBottom: '24px',
    },
    card: {
        background: theme.surface,
        border: `1px solid ${theme.border}`,
        borderRadius: '4px',
        padding: '20px',
    },
    cardTitle: {
        fontSize: '11px',
        letterSpacing: '3px',
        textTransform: 'uppercase',
        color: theme.textMuted,
        marginBottom: '16px',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
    },
    metricValue: {
        fontSize: '28px',
        fontWeight: '700',
        letterSpacing: '-1px',
    },
    metricLabel: {
        fontSize: '11px',
        color: theme.textMuted,
        letterSpacing: '2px',
        textTransform: 'uppercase',
        marginTop: '4px',
    },
    table: {
        width: '100%',
        borderCollapse: 'collapse',
        fontSize: '13px',
    },
    th: {
        textAlign: 'left',
        padding: '8px 12px',
        fontSize: '10px',
        letterSpacing: '2px',
        textTransform: 'uppercase',
        color: theme.textMuted,
        borderBottom: `1px solid ${theme.border}`,
    },
    td: {
        padding: '12px',
        borderBottom: `1px solid ${theme.border}`,
        color: theme.text,
    },
    portfolioTab: (active) => ({
        padding: '8px 20px',
        background: active ? theme.accent : 'transparent',
        color: active ? theme.bg : theme.textDim,
        border: `1px solid ${active ? theme.accent : theme.border}`,
        borderRadius: '2px',
        cursor: 'pointer',
        fontSize: '12px',
        letterSpacing: '1px',
        fontFamily: 'inherit',
        transition: 'all 0.2s',
    }),
    dot: (color) => ({
        width: '6px',
        height: '6px',
        borderRadius: '50%',
        background: color,
        display: 'inline-block',
        boxShadow: `0 0 6px ${color}`,
    }),
    badge: (color) => ({
        display: 'inline-block',
        padding: '2px 8px',
        fontSize: '10px',
        letterSpacing: '1px',
        borderRadius: '2px',
        background: color + '22',
        color: color,
        border: `1px solid ${color}44`,
        textTransform: 'uppercase',
    }),
    alertItem: (severity) => ({
        padding: '12px 16px',
        marginBottom: '8px',
        borderLeft: `3px solid ${severity === 'HIGH' ? theme.accentRed : theme.accentYellow}`,
        background: theme.surfaceAlt,
        borderRadius: '0 4px 4px 0',
        fontSize: '13px',
    }),
    sectionHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '24px',
    },
    refreshBtn: {
        padding: '6px 14px',
        background: 'transparent',
        border: `1px solid ${theme.accent}`,
        color: theme.accent,
        borderRadius: '2px',
        cursor: 'pointer',
        fontSize: '11px',
        letterSpacing: '2px',
        fontFamily: 'inherit',
        textTransform: 'uppercase',
    },
};

export default function App() {
    const [portfolios, setPortfolios] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [analytics, setAnalytics] = useState(null);
    const [risk, setRisk] = useState(null);
    const [sentiment, setSentiment] = useState(null);
    const [alerts, setAlerts] = useState([]);
    const [connected, setConnected] = useState(false);
    const [loading, setLoading] = useState(true);

    // Load all portfolios on mount
    useEffect(() => {
        axios.get(`${API_BASE}/api/portfolios`)
            .then(res => {
                setPortfolios(res.data);
                if (res.data.length > 0) setSelectedId(res.data[0].id);
            })
            .catch(err => console.error('Failed to load portfolios:', err))
            .finally(() => setLoading(false));
    }, []);

    // Load analytics + sentiment when selected portfolio changes
    useEffect(() => {
        if (!selectedId) return;
        setAnalytics(null);
        setRisk(null);
        setSentiment(null);

        Promise.all([
            axios.get(`${API_BASE}/api/portfolios/${selectedId}/analytics`),
            axios.get(`${API_BASE}/api/portfolios/${selectedId}/risk`).catch(() => null),
            axios.get(`${API_BASE}/api/portfolios/${selectedId}/sentiment`).catch(() => null),
        ]).then(([analyticsRes, riskRes, sentimentRes]) => {
            setAnalytics(analyticsRes.data);
            if (riskRes) setRisk(riskRes.data);
            if (sentimentRes) setSentiment(sentimentRes.data);
        }).catch(err => console.error('Failed to fetch data:', err));
    }, [selectedId]);

    // WebSocket connection
    useEffect(() => {
        if (!selectedId) return;
        const client = new Client({
            webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
            onConnect: () => {
                setConnected(true);
                client.subscribe(`/topic/alerts/${selectedId}`, (msg) => {
                    const alert = JSON.parse(msg.body);
                    setAlerts(prev => [alert, ...prev].slice(0, 20));
                });
            },
            onDisconnect: () => setConnected(false),
            reconnectDelay: 5000,
        });
        client.activate();
        return () => client.deactivate();
    }, [selectedId]);

    const selectedPortfolio = portfolios.find(p => p.id === selectedId);

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

    const fmt = (n, prefix = '') => n != null ? `${prefix}${Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—';
    const pct = (n) => n != null ? `${Number(n).toFixed(2)}%` : '—';

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
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '12px' }}>
                        <span style={styles.dot(connected ? theme.accentGreen : theme.accentRed)} />
                        <span style={{ color: connected ? theme.accentGreen : theme.accentRed }}>
              {connected ? 'LIVE' : 'DISCONNECTED'}
            </span>
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
                        <button style={styles.refreshBtn} onClick={handleRefresh}>⟳ Refresh Prices</button>
                    </div>
                )}

                {/* Key Metrics */}
                {analytics && (
                    <div style={styles.grid4}>
                        <MetricCard label="Portfolio Value" value={fmt(analytics.portfolioValue, '$')} color={theme.accent} />
                        <MetricCard label="Daily P&L" value={fmt(analytics.pnl, analytics.pnl >= 0 ? '+$' : '-$').replace('--', '-')} color={analytics.pnl >= 0 ? theme.accentGreen : theme.accentRed} />
                        <MetricCard label="Total Return" value={pct(analytics.totalReturn)} color={analytics.totalReturn >= 0 ? theme.accentGreen : theme.accentRed} />
                        <MetricCard label="Sharpe Ratio" value={analytics.sharpeRatio?.toFixed(3) ?? '—'} color={analytics.sharpeRatio > 1 ? theme.accentGreen : theme.accentYellow} />
                    </div>
                )}

                {/* Risk Metrics Row */}
                {risk && (
                    <div style={{ ...styles.grid4, marginBottom: '24px' }}>
                        <MetricCard label="VaR 95%" value={pct(risk.var95)} color={theme.accentRed} small />
                        <MetricCard label="VaR 99%" value={pct(risk.var99)} color={theme.accentRed} small />
                        <MetricCard label="Portfolio Beta" value={risk.beta?.toFixed(3) ?? '—'} color={theme.accentYellow} small />
                        <MetricCard label="Stock Count" value={risk.stockCount ?? '—'} color={theme.textDim} small />
                    </div>
                )}

                {/* Positions Table + Sentiment */}
                <div style={styles.grid2}>

                    {/* Positions */}
                    <div style={styles.card}>
                        <div style={styles.cardTitle}>
                            <span style={styles.dot(theme.accent)} /> Positions
                        </div>
                        {selectedPortfolio?.stocks?.length > 0 ? (
                            <table style={styles.table}>
                                <thead>
                                <tr>
                                    {['Ticker', 'Sector', 'Qty', 'Purchase', 'Current', 'P&L'].map(h => (
                                        <th key={h} style={styles.th}>{h}</th>
                                    ))}
                                </tr>
                                </thead>
                                <tbody>
                                {selectedPortfolio.stocks.map(stock => {
                                    const pnl = (stock.currentPrice - stock.purchasePrice) * stock.quantity;
                                    const pnlPct = ((stock.currentPrice - stock.purchasePrice) / stock.purchasePrice) * 100;
                                    return (
                                        <tr key={stock.id}>
                                            <td style={{ ...styles.td, color: theme.accent, fontWeight: '700' }}>{stock.ticker}</td>
                                            <td style={{ ...styles.td, color: theme.textDim, fontSize: '11px' }}>{stock.sector}</td>
                                            <td style={styles.td}>{stock.quantity}</td>
                                            <td style={styles.td}>${stock.purchasePrice.toFixed(2)}</td>
                                            <td style={styles.td}>${stock.currentPrice.toFixed(2)}</td>
                                            <td style={{ ...styles.td, color: pnl >= 0 ? theme.accentGreen : theme.accentRed, fontWeight: '600' }}>
                                                {pnl >= 0 ? '+' : ''}{fmt(pnl, '$')} ({pnlPct.toFixed(1)}%)
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                        ) : (
                            <div style={{ color: theme.textMuted, fontSize: '13px' }}>No positions in this portfolio.</div>
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
                            <div style={{ color: theme.textMuted, fontSize: '13px' }}>Loading sentiment data...</div>
                        )}
                    </div>
                </div>

                {/* Live Alerts */}
                <div style={styles.card}>
                    <div style={{ ...styles.sectionHeader }}>
                        <div style={styles.cardTitle}>
                            <span style={styles.dot(theme.accentRed)} /> Live Risk Alerts
                            {alerts.length > 0 && <span style={styles.badge(theme.accentRed)}>{alerts.length}</span>}
                        </div>
                    </div>
                    {alerts.length === 0 ? (
                        <div style={{ color: theme.textMuted, fontSize: '13px', padding: '8px 0' }}>
                            ✓ No anomalies detected — portfolio behavior is within normal parameters
                        </div>
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
                                    <span style={styles.badge(alert.severity === 'HIGH' ? theme.accentRed : theme.accentYellow)}>
                    {alert.severity}
                  </span>
                                </div>
                            </div>
                        ))
                    )}
                </div>

            </div>
        </div>
    );
}

function MetricCard({ label, value, color, small }) {
    return (
        <div style={{
            background: theme.surface,
            border: `1px solid ${theme.border}`,
            borderTop: `2px solid ${color}`,
            borderRadius: '4px',
            padding: small ? '16px' : '20px',
        }}>
            <div style={{ fontSize: '10px', letterSpacing: '2px', textTransform: 'uppercase', color: theme.textMuted, marginBottom: '8px' }}>{label}</div>
            <div style={{ fontSize: small ? '22px' : '28px', fontWeight: '700', color: color, letterSpacing: '-1px' }}>{value}</div>
        </div>
    );
}