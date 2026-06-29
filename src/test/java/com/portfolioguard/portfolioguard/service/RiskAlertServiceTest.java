package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.CorrelationAlert;
import com.portfolioguard.portfolioguard.model.Portfolio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskAlertServiceTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock AnomalyDetectionService anomalyDetectionService;
    @Mock CorrelationMonitorService correlationMonitorService;
    @Mock PortfolioService portfolioService;

    @InjectMocks RiskAlertService riskAlertService;

    @Test
    void checkAndBroadcastAlerts_noAnomalyNoCorrelation_doesNotBroadcast() {
        Portfolio p = emptyPortfolio("p1");
        when(anomalyDetectionService.detectAnomalies("p1"))
                .thenReturn(Map.of("is_current_anomalous", false));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(correlationMonitorService.detectCorrelationBreakdowns(p)).thenReturn(List.of());

        riskAlertService.checkAndBroadcastAlerts("p1");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void checkAndBroadcastAlerts_anomalyDetected_broadcastsAlert() {
        Portfolio p = emptyPortfolio("p1");
        when(anomalyDetectionService.detectAnomalies("p1"))
                .thenReturn(Map.of("is_current_anomalous", true, "current_score", -0.45));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(correlationMonitorService.detectCorrelationBreakdowns(p)).thenReturn(List.of());

        riskAlertService.checkAndBroadcastAlerts("p1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts/p1"), captor.capture());

        Map<String, Object> alert = captor.getValue();
        assertThat(alert.get("type")).isEqualTo("ML_ANOMALY");
        assertThat(alert.get("severity")).isEqualTo("HIGH");
        assertThat(alert.get("portfolioId")).isEqualTo("p1");
        assertThat(alert.get("score")).isEqualTo(-0.45);
    }

    @Test
    void checkAndBroadcastAlerts_correlationBreakdown_broadcastsForEachAlert() {
        Portfolio p = emptyPortfolio("p1");
        when(anomalyDetectionService.detectAnomalies("p1"))
                .thenReturn(Map.of("is_current_anomalous", false));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);

        CorrelationAlert alert1 = new CorrelationAlert("AAPL", "TSLA", 0.3, 0.8, 0.5, "HIGH", LocalDateTime.now());
        CorrelationAlert alert2 = new CorrelationAlert("NVDA", "AMD", 0.5, 0.9, 0.4, "MEDIUM", LocalDateTime.now());
        when(correlationMonitorService.detectCorrelationBreakdowns(p)).thenReturn(List.of(alert1, alert2));

        riskAlertService.checkAndBroadcastAlerts("p1");

        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/alerts/p1"), any(Map.class));
    }

    @Test
    void checkAndBroadcastAlerts_correlationAlert_containsCorrectFields() {
        Portfolio p = emptyPortfolio("p1");
        when(anomalyDetectionService.detectAnomalies("p1"))
                .thenReturn(Map.of("is_current_anomalous", false));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);

        CorrelationAlert corrAlert = new CorrelationAlert("AAPL", "TSLA", 0.3, 0.85, 0.55, "HIGH", LocalDateTime.now());
        when(correlationMonitorService.detectCorrelationBreakdowns(p)).thenReturn(List.of(corrAlert));

        riskAlertService.checkAndBroadcastAlerts("p1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts/p1"), captor.capture());

        Map<String, Object> alert = captor.getValue();
        assertThat(alert.get("type")).isEqualTo("CORRELATION_BREAKDOWN");
        assertThat(alert.get("severity")).isEqualTo("HIGH");
        assertThat((String) alert.get("message")).contains("AAPL").contains("TSLA");
    }

    @Test
    void checkAndBroadcastAlerts_bothAnomalyAndCorrelation_broadcastsBoth() {
        Portfolio p = emptyPortfolio("p1");
        when(anomalyDetectionService.detectAnomalies("p1"))
                .thenReturn(Map.of("is_current_anomalous", true, "current_score", -0.5));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);

        CorrelationAlert corrAlert = new CorrelationAlert("AAPL", "TSLA", 0.2, 0.8, 0.6, "HIGH", LocalDateTime.now());
        when(correlationMonitorService.detectCorrelationBreakdowns(p)).thenReturn(List.of(corrAlert));

        riskAlertService.checkAndBroadcastAlerts("p1");

        // 1 for anomaly + 1 for correlation = 2 total
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/alerts/p1"), any(Map.class));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Portfolio emptyPortfolio(String id) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setUserId("u1");
        p.setStocks(new java.util.ArrayList<>());
        return p;
    }
}
