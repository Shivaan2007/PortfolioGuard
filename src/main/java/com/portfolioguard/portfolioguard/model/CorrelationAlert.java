package com.portfolioguard.portfolioguard.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CorrelationAlert {

    private String tickerA;
    private String tickerB;
    private double currentCorrelation;
    private double baselineCorrelation;
    private double deviation;
    private String severity;
    private LocalDateTime detectedAt;
}
