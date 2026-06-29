package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.Portfolio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnomalyDetectionService {

    @Value("${risk.engine.url:http://localhost:5001}")
    private String riskEngineUrl;

    @Autowired
    private RiskMetricsService riskMetricsService;

    @Autowired
    private PortfolioService portfolioService;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> detectAnomalies(String portfolioId) {
        Portfolio portfolio = portfolioService.getPortfolio(portfolioId);

        if (portfolio.getStocks().isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("is_current_anomalous", false);
            empty.put("message", "No stocks in portfolio");
            return empty;
        }

        List<Map<String, Object>> metricsHistory = new ArrayList<>();

        List<Double> returns = riskMetricsService
                .getRealReturns(portfolio.getStocks().get(0).getTicker(), 30);
        List<Double> marketReturns = riskMetricsService
                .getRealReturns("SPY", 30);

        for (int i = 0; i < returns.size(); i++) {
            Map<String, Object> dayMetrics = new HashMap<>();
            dayMetrics.put("daily_return", returns.get(i));
            dayMetrics.put("sharpe", riskMetricsService
                    .calculateSharpeRatio(returns.subList(0, i + 1)));
            dayMetrics.put("beta", riskMetricsService
                    .calculateBeta(returns.subList(0, i + 1),
                            marketReturns.subList(0, i + 1)));
            dayMetrics.put("var95", riskMetricsService
                    .calculateVaR(returns.subList(0, i + 1), 0.95));
            dayMetrics.put("avg_correlation", 0.65);
            metricsHistory.add(dayMetrics);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("metrics_history", metricsHistory);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    riskEngineUrl + "/detect-anomalies",
                    requestBody,
                    Map.class
            );
            return response;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Risk engine unavailable: " + e.getMessage());
            error.put("is_current_anomalous", false);
            return error;
        }
    }
}
