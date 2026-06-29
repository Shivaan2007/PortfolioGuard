package com.portfolioguard.portfolioguard.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class PriceEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PriceEventProducer.class);
    private static final String TOPIC = "price-updates";

    @Autowired
    private KafkaTemplate<String, PriceUpdateEvent> kafkaTemplate;

    public void publishPriceUpdate(String ticker, double oldPrice,
                                   double newPrice, String portfolioId) {
        double changePercent = oldPrice == 0 ? 0
                : ((newPrice - oldPrice) / oldPrice) * 100;

        PriceUpdateEvent event = new PriceUpdateEvent(
                ticker,
                oldPrice,
                newPrice,
                changePercent,
                portfolioId,
                LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC, ticker, event);
        log.info("Published price update for {}: ${} → ${} ({:.2f}%)", ticker, oldPrice, newPrice, changePercent);
    }
}