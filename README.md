# PortfolioGuard

**Institutional portfolio risk monitoring system** modeled on BlackRock's Aladdin platform. Real-time risk metrics, ML anomaly detection, and live WebSocket alerts — built with Java/Spring Boot, Kafka, Python microservices, and React.

---

## Architecture

---

## What it does

**Portfolio management** — create portfolios with stock positions (ticker, quantity, purchase price, sector).

**Real-time market data** — Alpha Vantage integration fetches live prices. Prices cached in Redis. Every update publishes a Kafka event through the `price-updates` topic.

**Financial risk metrics** computed on every portfolio:
- **Daily P&L** — current value minus cost basis across all positions
- **Total Return** — percentage gain/loss since portfolio creation
- **Sharpe Ratio** — return per unit of risk (>1.0 is good, >3.0 is exceptional)
- **Value at Risk (VaR)** — worst expected loss at 95% and 99% confidence
- **Portfolio Beta** — sensitivity to S&P 500 market movements
- **Correlation Matrix** — cross-stock correlation to detect hidden concentration risk

**ML anomaly detection** via Python microservice:
- **Isolation Forest** — monitors 5 metrics simultaneously and flags statistically unusual behavior
- **Correlation breakdown detection** — rolling 60-day vs 252-day baseline comparison
- **VaR breach detection** — alerts when actual loss exceeds predicted threshold

**Sentiment analysis** — NewsAPI headlines scored with NLP per stock (POSITIVE / NEUTRAL / NEGATIVE)

**Real-time alerts** — WebSocket broadcasts to React dashboard without page refresh

**PDF reports** — downloadable reports with positions, metrics, risk data, and sentiment

**Swagger UI** — interactive API docs at `/swagger-ui/index.html`

---

## Technology stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3 |
| Database | PostgreSQL 15 |
| Cache | Redis 7 |
| Event streaming | Apache Kafka |
| ML/anomaly detection | Python 3, Scikit-learn (Isolation Forest) |
| NLP/sentiment | Python 3, TextBlob, NewsAPI |
| Frontend | React 18, Axios, STOMP/SockJS |
| Infrastructure | Docker, docker-compose |
| API docs | SpringDoc OpenAPI 3 (Swagger UI) |

---

## Getting started

### Prerequisites
- Docker Desktop
- Java 17 (for local builds)
- Node.js 18+ (for frontend)

### Run the full stack

```bash
git clone https://github.com/yourusername/portfolioguard.git
cd portfolioguard
docker-compose up -d
```

| Service | URL |
|---------|-----|
| React dashboard | http://localhost:3000 |
| Spring Boot API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Risk engine | http://localhost:5001 |
| Sentiment service | http://localhost:5002 |

### Run frontend locally

```bash
cd frontend && npm install && npm start
```

---

## API reference
---

## WebSocket alerts

```javascript
const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  onConnect: () => {
    client.subscribe('/topic/alerts/{portfolioId}', (message) => {
      const alert = JSON.parse(message.body);
      console.log(alert.type, alert.message, alert.severity);
    });
  }
});
client.activate();
```

Alert types: `PRICE_UPDATE`, `ML_ANOMALY`, `CORRELATION_BREAKDOWN`, `VAR_BREACH`

---

## Key design decisions

**Why Kafka?** Price updates are decoupled from alert processing — the consumer scales independently and events replay on restart.

**Why Redis?** Alpha Vantage has strict rate limits. Redis caches prices with a 1-hour TTL to avoid exhausting the quota.

**Why Python for ML?** Scikit-learn's Isolation Forest is the right tool. Rather than reimplement it in Java, the Python service exposes a REST endpoint the Spring Boot backend calls.

**Why Isolation Forest?** Unsupervised algorithm — no labeled training data needed. "Normal" behavior is defined by the portfolio's own history.

---

## Interview talking points

> "PortfolioGuard is a real-time portfolio risk monitoring system modeled on BlackRock's Aladdin platform. It computes institutional risk metrics including Value at Risk, Sharpe Ratio, and Portfolio Beta on live market data, uses Isolation Forest ML to detect anomalous portfolio behavior across five risk dimensions, streams price updates through Kafka, and delivers real-time alerts via WebSocket to a React dashboard. The backend is Java/Spring Boot with PostgreSQL and Redis, with Python microservices handling the ML and NLP layers — all containerized with Docker."

---

## License

MIT
