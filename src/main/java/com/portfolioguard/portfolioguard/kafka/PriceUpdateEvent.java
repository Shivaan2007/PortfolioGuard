package com.portfolioguard.portfolioguard.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PriceUpdateEvent {

    private String ticker;
    private double oldPrice;
    private double newPrice;
    private double changePercent;
    private String portfolioId;
    private LocalDateTime timestamp;
}
