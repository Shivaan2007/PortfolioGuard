package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.CorrelationAlert;
import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationMonitorServiceTest {

    @Mock RiskMetricsService riskMetricsService;
    @Mock MarketDataService marketDataService;

    @InjectMocks CorrelationMonitorService correlationMonitorService;

    @Test
    void detectCorrelationBreakdowns_noStocks_returnsEmpty() {
        Portfolio p = portfolioWithStocks();
        assertThat(correlationMonitorService.detectCorrelationBreakdowns(p)).isEmpty();
    }

    @Test
    void detectCorrelationBreakdowns_oneStock_returnsEmpty() {
        Portfolio p = portfolioWithStocks("AAPL");
        assertThat(correlationMonitorService.detectCorrelationBreakdowns(p)).isEmpty();
    }

    @Test
    void detectCorrelationBreakdowns_twoStocks_noBreakdown_returnsEmpty() {
        Portfolio p = portfolioWithStocks("AAPL", "TSLA");
        List<Double> aapl = uniformReturns(90, 0.01);
        List<Double> tsla = uniformReturns(90, 0.01);
        when(riskMetricsService.getRealReturns(eq("AAPL"), eq(90))).thenReturn(aapl);
        when(riskMetricsService.getRealReturns(eq("TSLA"), eq(90))).thenReturn(tsla);
        // Correlation of identical series = 1.0; recent 30-day slice of same series = 1.0; deviation = 0
        when(riskMetricsService.calculateCorrelation(any(), any())).thenReturn(0.8);

        List<CorrelationAlert> alerts = correlationMonitorService.detectCorrelationBreakdowns(p);
        assertThat(alerts).isEmpty();
    }

    @Test
    void detectCorrelationBreakdowns_twoStocks_breakdownDetected_returnsAlert() {
        Portfolio p = portfolioWithStocks("AAPL", "TSLA");
        List<Double> aapl = uniformReturns(90, 0.01);
        List<Double> tsla = uniformReturns(90, 0.02);
        when(riskMetricsService.getRealReturns(eq("AAPL"), eq(90))).thenReturn(aapl);
        when(riskMetricsService.getRealReturns(eq("TSLA"), eq(90))).thenReturn(tsla);
        // Baseline = 0.8, current = 0.2 → deviation = 0.6 → HIGH (threshold > 0.5)
        when(riskMetricsService.calculateCorrelation(any(), any()))
                .thenReturn(0.8)   // baseline call
                .thenReturn(0.2);  // current call

        List<CorrelationAlert> alerts = correlationMonitorService.detectCorrelationBreakdowns(p);

        assertThat(alerts).hasSize(1);
        CorrelationAlert alert = alerts.get(0);
        assertThat(alert.getTickerA()).isEqualTo("AAPL");
        assertThat(alert.getTickerB()).isEqualTo("TSLA");
        assertThat(alert.getSeverity()).isEqualTo("HIGH");
    }

    @Test
    void detectCorrelationBreakdowns_mediumSeverity_whenDeviationBetween30And50Pct() {
        Portfolio p = portfolioWithStocks("AAPL", "TSLA");
        List<Double> returns = uniformReturns(90, 0.01);
        when(riskMetricsService.getRealReturns(anyString(), anyInt())).thenReturn(returns);
        // Baseline = 0.75, current = 0.4 → deviation = 0.35 → > 0.3 threshold, ≤ 0.5 → MEDIUM
        when(riskMetricsService.calculateCorrelation(any(), any()))
                .thenReturn(0.75)  // baseline call
                .thenReturn(0.4);  // current call

        List<CorrelationAlert> alerts = correlationMonitorService.detectCorrelationBreakdowns(p);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void detectCorrelationBreakdowns_exceptionForOnePair_continuesOtherPairs() {
        Portfolio p = portfolioWithStocks("AAPL", "TSLA", "NVDA");
        when(riskMetricsService.getRealReturns(eq("AAPL"), anyInt()))
                .thenThrow(new RuntimeException("price data unavailable"));
        when(riskMetricsService.getRealReturns(eq("TSLA"), anyInt())).thenReturn(uniformReturns(90, 0.01));
        when(riskMetricsService.getRealReturns(eq("NVDA"), anyInt())).thenReturn(uniformReturns(90, 0.02));
        when(riskMetricsService.calculateCorrelation(any(), any())).thenReturn(0.5);

        // Should not throw; TSLA-NVDA pair still processed
        List<CorrelationAlert> alerts = correlationMonitorService.detectCorrelationBreakdowns(p);
        // No alert since deviation = 0 (0.5 - 0.5)
        assertThat(alerts).isEmpty();
    }

    @Test
    void detectCorrelationBreakdowns_threeStocks_checksAllPairs() {
        Portfolio p = portfolioWithStocks("AAPL", "TSLA", "NVDA");
        List<Double> returns = uniformReturns(90, 0.01);
        when(riskMetricsService.getRealReturns(anyString(), anyInt())).thenReturn(returns);
        // Large deviation for all pairs
        when(riskMetricsService.calculateCorrelation(any(), any()))
                .thenReturn(0.9, 0.1, 0.9, 0.1, 0.9, 0.1);

        List<CorrelationAlert> alerts = correlationMonitorService.detectCorrelationBreakdowns(p);
        // 3 pairs: AAPL-TSLA, AAPL-NVDA, TSLA-NVDA — each with deviation 0.8
        assertThat(alerts).hasSize(3);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Portfolio portfolioWithStocks(String... tickers) {
        Portfolio p = new Portfolio();
        p.setId("p1");
        p.setUserId("u1");
        p.setStocks(new ArrayList<>());
        for (String ticker : tickers) {
            Stock s = new Stock();
            s.setTicker(ticker);
            p.getStocks().add(s);
        }
        return p;
    }

    private List<Double> uniformReturns(int size, double value) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(value * (i % 3 == 0 ? 1 : -1));
        return list;
    }
}
