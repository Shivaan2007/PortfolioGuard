package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.CorrelationAlert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RiskAlertService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Autowired
    private CorrelationMonitorService correlationMonitorService;

    @Autowired
    private PortfolioService portfolioService;

    public void checkAndBroadcastAlerts(String portfolioId) {

        // Check anomaly detection
        Map<String, Object> anomalyResult = anomalyDetectionService
                .detectAnomalies(portfolioId);

        Boolean isAnomalous = (Boolean) anomalyResult.get("is_current_anomalous");

        if (Boolean.TRUE.equals(isAnomalous)) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "ML_ANOMALY");
            alert.put("portfolioId", portfolioId);
            alert.put("message", "Isolation Forest detected anomalous portfolio behavior");
            alert.put("severity", "HIGH");
            alert.put("score", anomalyResult.get("current_score"));
            alert.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend(
                    "/topic/alerts/" + portfolioId, alert);

            System.out.println("🚨 WebSocket alert broadcast for portfolio: "
                    + portfolioId);
        }

        // Check correlation breakdowns
        List<CorrelationAlert> correlationAlerts = correlationMonitorService
                .detectCorrelationBreakdowns(
                        portfolioService.getPortfolio(portfolioId));

        for (CorrelationAlert corrAlert : correlationAlerts) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "CORRELATION_BREAKDOWN");
            alert.put("portfolioId", portfolioId);
            alert.put("message", corrAlert.getTickerA() + " and "
                    + corrAlert.getTickerB()
                    + " correlation breakdown detected");
            alert.put("severity", corrAlert.getSeverity());
            alert.put("currentCorrelation", corrAlert.getCurrentCorrelation());
            alert.put("baselineCorrelation", corrAlert.getBaselineCorrelation());
            alert.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend(
                    "/topic/alerts/" + portfolioId, alert);
        }
    }
}