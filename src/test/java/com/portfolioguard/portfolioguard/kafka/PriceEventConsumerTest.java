package com.portfolioguard.portfolioguard.kafka;

import com.portfolioguard.portfolioguard.service.RiskAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceEventConsumerTest {

    @Mock RiskAlertService riskAlertService;
    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks PriceEventConsumer consumer;

    @Test
    void consumePriceUpdate_smallMove_broadcastsPriceUpdateToWebSocket() {
        PriceUpdateEvent event = event("AAPL", 140.0, 141.0, 0.71, "p1");

        consumer.consumePriceUpdate(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts/p1"), captor.capture());

        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("type")).isEqualTo("PRICE_UPDATE");
        assertThat(payload.get("portfolioId")).isEqualTo("p1");
        assertThat(payload.get("severity")).isEqualTo("LOW");
        assertThat((String) payload.get("message")).contains("AAPL");
    }

    @Test
    void consumePriceUpdate_significantMove_triggersRiskAlertCheck() {
        PriceUpdateEvent event = event("TSLA", 200.0, 207.0, 3.5, "p1");

        consumer.consumePriceUpdate(event);

        verify(riskAlertService).checkAndBroadcastAlerts("p1");
    }

    @Test
    void consumePriceUpdate_significantMove_setsHighSeverity() {
        PriceUpdateEvent event = event("TSLA", 200.0, 207.0, 3.5, "p1");

        consumer.consumePriceUpdate(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
        assertThat(captor.getValue().get("severity")).isEqualTo("HIGH");
    }

    @Test
    void consumePriceUpdate_smallMove_doesNotTriggerRiskAlertCheck() {
        PriceUpdateEvent event = event("AAPL", 150.0, 151.0, 0.67, "p1");

        consumer.consumePriceUpdate(event);

        verify(riskAlertService, never()).checkAndBroadcastAlerts(anyString());
    }

    @Test
    void consumePriceUpdate_alertCheckThrows_exceptionSwallowedWithoutPropagating() {
        PriceUpdateEvent event = event("NVDA", 400.0, 420.0, 5.0, "p1");
        doThrow(new RuntimeException("anomaly engine down"))
                .when(riskAlertService).checkAndBroadcastAlerts("p1");

        assertThatCode(() -> consumer.consumePriceUpdate(event)).doesNotThrowAnyException();
        // WebSocket broadcast still happened
        verify(messagingTemplate).convertAndSend(eq("/topic/alerts/p1"), any(Map.class));
    }

    @Test
    void consumePriceUpdate_negativeSignificantMove_triggersAlert() {
        // A -3% move should also be a significant move
        PriceUpdateEvent event = event("AAPL", 200.0, 193.0, -3.5, "p1");

        consumer.consumePriceUpdate(event);

        verify(riskAlertService).checkAndBroadcastAlerts("p1");
    }

    @Test
    void consumePriceUpdate_alwaysBroadcastsTimestamp() {
        PriceUpdateEvent event = event("AAPL", 150.0, 151.0, 0.67, "p1");

        consumer.consumePriceUpdate(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());
        assertThat(captor.getValue().get("timestamp")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private PriceUpdateEvent event(String ticker, double old, double newP, double pct, String portfolioId) {
        return new PriceUpdateEvent(ticker, old, newP, pct, portfolioId, LocalDateTime.now());
    }
}
