package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.CorrelationAlert;
import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CorrelationMonitorService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationMonitorService.class);

    @Autowired
    private RiskMetricsService riskMetricsService;

    @Autowired
    private MarketDataService marketDataService;

    private static final double BREAKDOWN_THRESHOLD = 0.3;
    private static final int BASELINE_DAYS = 90;
    private static final int CURRENT_DAYS = 30;

    public List<CorrelationAlert> detectCorrelationBreakdowns(Portfolio portfolio) {
        List<CorrelationAlert> alerts = new ArrayList<>();
        List<Stock> stocks = portfolio.getStocks();

        if (stocks.size() < 2) {
            return alerts;
        }

        for (int i = 0; i < stocks.size(); i++) {
            for (int j = i + 1; j < stocks.size(); j++) {

                String tickerA = stocks.get(i).getTicker();
                String tickerB = stocks.get(j).getTicker();

                try {
                    List<Double> returnsA90 = riskMetricsService
                            .getRealReturns(tickerA, BASELINE_DAYS);
                    List<Double> returnsB90 = riskMetricsService
                            .getRealReturns(tickerB, BASELINE_DAYS);

                    List<Double> returnsA30 = returnsA90.subList(
                            Math.max(0, returnsA90.size() - CURRENT_DAYS),
                            returnsA90.size());
                    List<Double> returnsB30 = returnsB90.subList(
                            Math.max(0, returnsB90.size() - CURRENT_DAYS),
                            returnsB90.size());

                    // Make sure both lists are same size for correlation calculation
                    int minSize90 = Math.min(returnsA90.size(), returnsB90.size());
                    int minSize30 = Math.min(returnsA30.size(), returnsB30.size());

                    List<Double> safeA90 = returnsA90.subList(0, minSize90);
                    List<Double> safeB90 = returnsB90.subList(0, minSize90);
                    List<Double> safeA30 = returnsA30.subList(0, minSize30);
                    List<Double> safeB30 = returnsB30.subList(0, minSize30);

                    double baselineCorr = riskMetricsService.calculateCorrelation(safeA90, safeB90);
                    double currentCorr = riskMetricsService.calculateCorrelation(safeA30, safeB30);

                    double deviation = Math.abs(currentCorr - baselineCorr);

                    if (deviation > BREAKDOWN_THRESHOLD) {
                        String severity = deviation > 0.5 ? "HIGH" : "MEDIUM";
                        alerts.add(new CorrelationAlert(
                                tickerA,
                                tickerB,
                                currentCorr,
                                baselineCorr,
                                deviation,
                                severity,
                                LocalDateTime.now()
                        ));
                    }

                } catch (Exception e) {
                    log.warn("Failed to check correlation for {}-{}: {}", tickerA, tickerB, e.getMessage());
                }
            }
        }
        return alerts;
    }
}
