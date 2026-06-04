package com.portfolioguard.portfolioguard.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class PriceEventProducer {

    private static final String TOPIC = "price-updates";

    @Autowired
    private KafkaTemplate<String, PriceUpdateEvent> kafkaTemplate;

    public void publishPriceUpdate(String ticker, double oldPrice,
                                   double newPrice, String portfolioId) {
        double changePercent = ((newPrice - oldPrice) / oldPrice) * 100;

        PriceUpdateEvent event = new PriceUpdateEvent(
                ticker,
                oldPrice,
                newPrice,
                changePercent,
                portfolioId,
                LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC, ticker, event);
        System.out.println("Published price update for " + ticker
                + ": $" + oldPrice + " → $" + newPrice
                + " (" + String.format("%.2f", changePercent) + "%)");
    }
}