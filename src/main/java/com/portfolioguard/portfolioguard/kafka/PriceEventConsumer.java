package com.portfolioguard.portfolioguard.kafka;

import com.portfolioguard.portfolioguard.service.RiskAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class PriceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PriceEventConsumer.class);
    private static final double SIGNIFICANT_MOVE_THRESHOLD = 2.0;

    @Autowired
    private RiskAlertService riskAlertService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "price-updates", groupId = "portfolioguard")
    public void consumePriceUpdate(PriceUpdateEvent event) {
        log.debug("Received price update: {} ${} → ${} ({}%)",
                event.getTicker(), event.getOldPrice(), event.getNewPrice(),
                String.format("%.2f", event.getChangePercent()));

        // Always broadcast a price update alert so dashboard shows activity
        Map<String, Object> priceAlert = new HashMap<>();
        priceAlert.put("type", "PRICE_UPDATE");
        priceAlert.put("portfolioId", event.getPortfolioId());
        priceAlert.put("message", event.getTicker() + " updated $"
                + String.format("%.2f", event.getOldPrice())
                + " → $" + String.format("%.2f", event.getNewPrice())
                + " (" + String.format("%.2f", event.getChangePercent()) + "%)");
        priceAlert.put("severity", Math.abs(event.getChangePercent()) > SIGNIFICANT_MOVE_THRESHOLD ? "HIGH" : "LOW");
        priceAlert.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend(
                "/topic/alerts/" + event.getPortfolioId(), priceAlert);

        // If significant move, also run full anomaly + correlation checks
        if (Math.abs(event.getChangePercent()) > SIGNIFICANT_MOVE_THRESHOLD) {
            log.warn("Significant move: {} moved {}%", event.getTicker(),
                    String.format("%.2f", event.getChangePercent()));
            try {
                riskAlertService.checkAndBroadcastAlerts(event.getPortfolioId());
            } catch (Exception e) {
                log.error("Alert check failed for portfolio {}: {}", event.getPortfolioId(), e.getMessage());
            }
        }
    }
}
