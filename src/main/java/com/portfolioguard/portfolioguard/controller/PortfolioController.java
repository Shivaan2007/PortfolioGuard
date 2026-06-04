package com.portfolioguard.portfolioguard.controller;

import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import com.portfolioguard.portfolioguard.service.PortfolioService;
import com.portfolioguard.portfolioguard.service.RiskMetricsService;
import com.portfolioguard.portfolioguard.service.MarketDataService;
import com.portfolioguard.portfolioguard.model.CorrelationAlert;
import com.portfolioguard.portfolioguard.service.CorrelationMonitorService;
import com.portfolioguard.portfolioguard.service.AnomalyDetectionService;
import com.portfolioguard.portfolioguard.service.RiskAlertService;
import com.portfolioguard.portfolioguard.service.SentimentService;
import com.portfolioguard.portfolioguard.service.PdfReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    @Autowired
    private PdfReportService pdfReportService;

    @Autowired
    private SentimentService sentimentService;

    @Autowired
    private RiskAlertService riskAlertService;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Autowired
    private CorrelationMonitorService correlationMonitorService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private RiskMetricsService riskMetricsService;

    @Autowired
    private MarketDataService marketDataService;

    @GetMapping
    public ResponseEntity<List<Portfolio>> getAllPortfolios() {
        return ResponseEntity.ok(portfolioService.getAllPortfolios());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("PortfolioGuard is running");
    }
    

    @PostMapping
    public ResponseEntity<Portfolio> createPortfolio(@RequestBody CreatePortfolioRequest request) {
        Portfolio portfolio = portfolioService.createPortfolio(
                request.name(),
                request.description(),
                request.strategy(),
                request.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolio);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Portfolio> getPortfolio(@PathVariable String id) {
        return ResponseEntity.ok(portfolioService.getPortfolio(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Portfolio>> getUserPortfolios(@PathVariable String userId) {
        return ResponseEntity.ok(portfolioService.getUserPortfolios(userId));
    }

    @PostMapping("/{id}/stocks")
    public ResponseEntity<Portfolio> addStock(@PathVariable String id,
                                              @RequestBody Stock stock) {
        return ResponseEntity.ok(portfolioService.addStock(id, stock));
    }

    @DeleteMapping("/{id}/stocks/{ticker}")
    public ResponseEntity<Portfolio> removeStock(@PathVariable String id,
                                                 @PathVariable String ticker) {
        return ResponseEntity.ok(portfolioService.removeStock(id, ticker));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePortfolio(@PathVariable String id) {
        portfolioService.deletePortfolio(id);
        return ResponseEntity.noContent().build();
    }

    record CreatePortfolioRequest(String name, String description,
                                  String strategy, String userId) {
    }

    @GetMapping("/{id}/analytics")
    public Map<String, Double> getAnalytics(
            @PathVariable String id) {

        Map<String, Double> analytics =
                new HashMap<>();

        analytics.put(
                "portfolioValue",
                portfolioService.calculatePortfolioValue(id)
        );

        analytics.put(
                "pnl",
                portfolioService.calculatePnL(id)
        );

        analytics.put(
                "totalReturn",
                portfolioService.calculateTotalReturn(id)
        );

        analytics.put(
                "sharpeRatio",
                portfolioService.calculateSharpeRatio(id)
        );

        return analytics;
    }


    @GetMapping("/{id}/risk")
    public ResponseEntity<Map<String, Object>> getRiskMetrics(@PathVariable String id) {

        // Get portfolio to know how many stocks
        Portfolio portfolio = portfolioService.getPortfolio(id);
        int stockCount = portfolio.getStocks().size();

        // Generate simulated historical returns until Alpha Vantage is connected
        List<Double> portfolioReturns = riskMetricsService.getRealReturns(
                portfolio.getStocks().get(0).getTicker(), 100);
        List<Double> marketReturns = riskMetricsService.getRealReturns("SPY", 100);

        // Calculate all risk metrics
        double var95 = riskMetricsService.calculateVaR(portfolioReturns, 0.95);
        double var99 = riskMetricsService.calculateVaR(portfolioReturns, 0.99);
        double beta = riskMetricsService.calculateBeta(portfolioReturns, marketReturns);
        double sharpe = riskMetricsService.calculateSharpeRatio(portfolioReturns);
        double[][] correlationMatrix = riskMetricsService.calculateCorrelationMatrix(stockCount);

        // Build response
        Map<String, Object> risk = new HashMap<>();
        risk.put("var95", var95);
        risk.put("var99", var99);
        risk.put("beta", beta);
        risk.put("sharpeRatio", sharpe);
        risk.put("correlationMatrix", correlationMatrix);
        risk.put("portfolioName", portfolio.getName());
        risk.put("stockCount", stockCount);

        return ResponseEntity.ok(risk);

    }

    @GetMapping("/market/{ticker}")
    public String getMarketData(
            @PathVariable String ticker) {

        return marketDataService.fetchStockData(ticker);
    }


    @GetMapping("/test/price/{ticker}")
    public ResponseEntity<String> testPrice(@PathVariable String ticker) {
        try {
            double price = marketDataService.getCurrentPrice(ticker);
            return ResponseEntity.ok("Price for " + ticker + ": $" + price);
        } catch (Exception e) {
            return ResponseEntity.ok("Error: " + e.getMessage());
        }
    }


    @GetMapping("/test/returns/{ticker}")
    public ResponseEntity<String> testReturns(@PathVariable String ticker) {
        try {
            List<Double> returns = marketDataService.getDailyReturns(ticker, 30);
            return ResponseEntity.ok("Got " + returns.size() + " returns for " + ticker + ". First: " + returns.get(0));
        } catch (Exception e) {
            return ResponseEntity.ok("Error: " + e.getMessage());
        }
    }


    @GetMapping("/test/raw/{ticker}")
    public ResponseEntity<String> testRaw(@PathVariable String ticker) {
        try {
            String raw = marketDataService.fetchRawDailyData(ticker);
            return ResponseEntity.ok(raw);
        } catch (Exception e) {
            return ResponseEntity.ok("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/refresh")
    public ResponseEntity<String> refreshPrices(@PathVariable String id) {
        portfolioService.refreshAndPublishPrices(id);
        return ResponseEntity.ok("Prices refreshed and events published for portfolio: " + id);
    }


    @GetMapping("/{id}/correlations/alerts")
    public ResponseEntity<List<CorrelationAlert>> getCorrelationAlerts(
            @PathVariable String id) {
        Portfolio portfolio = portfolioService.getPortfolio(id);
        List<CorrelationAlert> alerts = correlationMonitorService
                .detectCorrelationBreakdowns(portfolio);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/{id}/anomalies")
    public ResponseEntity<Map<String, Object>> getAnomalies(@PathVariable String id) {
        Map<String, Object> result = anomalyDetectionService.detectAnomalies(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/refresh-prices")
    public ResponseEntity<String> refreshPortfolio(
            @PathVariable String id) {

        portfolioService.refreshAndPublishPrices(id);

        riskAlertService.checkAndBroadcastAlerts(id);

        return ResponseEntity.ok(
                "Prices refreshed and alerts checked");
    }


    @GetMapping("/{id}/sentiment")
    public ResponseEntity<Map<String, Object>> getPortfolioSentiment(@PathVariable String id) {
        Portfolio portfolio = portfolioService.getPortfolio(id);
        Map<String, Object> sentimentResults = new HashMap<>();

        for (Stock stock : portfolio.getStocks()) {
            Map<String, Object> sentiment = sentimentService.getSentiment(stock.getTicker());
            sentimentResults.put(stock.getTicker(), sentiment);
        }

        return ResponseEntity.ok(sentimentResults);
    }
    @GetMapping("/{id}/report")
    public void generateReport(@PathVariable String id, HttpServletResponse response) throws Exception {
        byte[] pdf = pdfReportService.generateReport(id);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename=portfolio-report-" + id + ".pdf");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }
}
