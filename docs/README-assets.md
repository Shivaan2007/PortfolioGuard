# README Assets

This directory holds the architecture diagram and screenshot placeholders referenced from the root `README.md`.

## architecture.svg

Generated SVG showing the full system topology: React → Spring Boot → PostgreSQL / Redis / Kafka → Python microservices → external APIs. Edit the SVG directly to update service names, ports, or data-flow annotations.

## Replacing placeholder screenshots

The five SVG placeholders in `docs/images/` should be replaced with actual screenshots once the application is running. Each file name maps to the screenshot referenced in the README:

| File | Section in README | What to capture |
|---|---|---|
| `dashboard-overview.svg → .png` | Dashboard | Main portfolio view — positions table, live P&L row, WebSocket alert strip |
| `risk-metrics.svg → .png` | Dashboard | Risk panel — VaR 95/99, Beta, Sharpe, correlation matrix |
| `anomaly-detection.svg → .png` | Dashboard | Live alert feed with ML_ANOMALY and CORRELATION_BREAKDOWN events |
| `chat-panel.svg → .png` | Dashboard | Chat assistant open, showing a VaR or Beta explanation |
| `pdf-report.svg → .png` | Features | First page of the generated PDF institutional report |

### Recommended capture workflow

1. Start the full stack: `docker-compose up -d`
2. Open `http://localhost:3000`, register an account, create a portfolio with 3–5 positions.
3. Click **Refresh Prices** to trigger a Kafka price-update cycle and populate the alert feed.
4. Use a browser screenshot tool (e.g. Firefox full-page screenshot, or macOS `⌘⇧4`) at 1400 × 800 px.
5. Save each file as a PNG with the same base name as the placeholder (e.g., `dashboard-overview.png`).
6. Update the five `<img>` tags in `README.md` to use the `.png` extension:

```markdown
![Dashboard Overview](docs/images/dashboard-overview.png)
```

## Badge URLs

The README uses static shields.io badges. Update the GitHub path in each badge URL once the repo is public:

```
https://github.com/<your-github-username>/portfolioguard/actions/workflows/ci.yml/badge.svg
```

Replace `<your-github-username>` with the actual GitHub account name.

## Architecture diagram regeneration

The SVG was hand-authored. If the topology changes (new service, port change, new data path), edit `docs/architecture.svg` directly — the diagram is pure SVG with no external tooling required.
