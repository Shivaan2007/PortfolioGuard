package com.portfolioguard.portfolioguard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskMetricsServiceTest {

    @Mock MarketDataService marketDataService;
    @InjectMocks RiskMetricsService riskMetricsService;

    // ------------------------------------------------------------------
    // calculateVaR
    // ------------------------------------------------------------------

    @Test
    void calculateVaR_nullList_returnsZero() {
        assertThat(riskMetricsService.calculateVaR(null, 0.95)).isEqualTo(0.0);
    }

    @Test
    void calculateVaR_emptyList_returnsZero() {
        assertThat(riskMetricsService.calculateVaR(Collections.emptyList(), 0.95)).isEqualTo(0.0);
    }

    @Test
    void calculateVaR_95pct_picksCorrectPercentile() {
        // 10 returns sorted: [-5,-4,-3,-2,-1,0,1,2,3,4]
        // 95% VaR: index = floor((1-0.95)*10) = floor(0.5) = 0 → -5 → returns -500 (percent)
        List<Double> returns = List.of(-0.01, -0.02, 0.01, 0.02, -0.03, 0.03, -0.04, 0.04, -0.05, 0.05);
        double var = riskMetricsService.calculateVaR(returns, 0.95);
        assertThat(var).isCloseTo(-5.0, within(0.001));
    }

    @Test
    void calculateVaR_99pct_moreConservative() {
        List<Double> returns = List.of(-0.01, -0.02, 0.01, 0.02, -0.03, 0.03, -0.04, 0.04, -0.05, 0.05);
        double var95 = riskMetricsService.calculateVaR(returns, 0.95);
        double var99 = riskMetricsService.calculateVaR(returns, 0.99);
        // 99% VaR should be equal or more negative than 95% VaR
        assertThat(var99).isLessThanOrEqualTo(var95);
    }

    // ------------------------------------------------------------------
    // calculateBeta
    // ------------------------------------------------------------------

    @Test
    void calculateBeta_perfectCorrelation_returnsOne() {
        List<Double> r = List.of(0.01, -0.02, 0.03, -0.01, 0.02);
        assertThat(riskMetricsService.calculateBeta(r, r)).isCloseTo(1.0, within(0.001));
    }

    @Test
    void calculateBeta_emptyLists_returnsDefaultOne() {
        assertThat(riskMetricsService.calculateBeta(List.of(), List.of())).isEqualTo(1.0);
    }

    @Test
    void calculateBeta_mismatchedSizes_returnsDefaultOne() {
        assertThat(riskMetricsService.calculateBeta(List.of(0.01), List.of(0.01, 0.02))).isEqualTo(1.0);
    }

    @Test
    void calculateBeta_zeroMarketVariance_returnsDefaultOne() {
        // All market returns identical → zero variance → beta = 1.0
        List<Double> portfolio = List.of(0.01, 0.02, 0.01);
        List<Double> market = List.of(0.0, 0.0, 0.0);
        assertThat(riskMetricsService.calculateBeta(portfolio, market)).isEqualTo(1.0);
    }

    @Test
    void calculateBeta_aggressivePortfolio_betaAboveOne() {
        // Portfolio moves twice as much as market
        List<Double> market = List.of(0.01, -0.01, 0.02, -0.02);
        List<Double> portfolio = List.of(0.02, -0.02, 0.04, -0.04);
        assertThat(riskMetricsService.calculateBeta(portfolio, market)).isCloseTo(2.0, within(0.001));
    }

    // ------------------------------------------------------------------
    // calculateCorrelation
    // ------------------------------------------------------------------

    @Test
    void calculateCorrelation_perfectlyCorrelated_returnsOne() {
        List<Double> a = List.of(0.01, 0.02, -0.01, -0.02, 0.03);
        // b = 2*a
        List<Double> b = List.of(0.02, 0.04, -0.02, -0.04, 0.06);
        assertThat(riskMetricsService.calculateCorrelation(a, b)).isCloseTo(1.0, within(0.001));
    }

    @Test
    void calculateCorrelation_perfectlyAntiCorrelated_returnsNegativeOne() {
        List<Double> a = List.of(0.01, 0.02, -0.01, -0.02, 0.03);
        List<Double> b = List.of(-0.01, -0.02, 0.01, 0.02, -0.03);
        assertThat(riskMetricsService.calculateCorrelation(a, b)).isCloseTo(-1.0, within(0.001));
    }

    @Test
    void calculateCorrelation_emptyLists_returnsZero() {
        assertThat(riskMetricsService.calculateCorrelation(List.of(), List.of())).isEqualTo(0.0);
    }

    @Test
    void calculateCorrelation_mismatchedSizes_returnsZero() {
        assertThat(riskMetricsService.calculateCorrelation(List.of(0.01), List.of(0.01, 0.02))).isEqualTo(0.0);
    }

    @Test
    void calculateCorrelation_constantSeries_returnsZero() {
        // Zero std deviation → correlation undefined → returns 0
        List<Double> constant = List.of(0.01, 0.01, 0.01);
        List<Double> varying  = List.of(0.01, 0.02, 0.03);
        assertThat(riskMetricsService.calculateCorrelation(constant, varying)).isEqualTo(0.0);
    }

    // ------------------------------------------------------------------
    // calculateSharpeRatio
    // ------------------------------------------------------------------

    @Test
    void calculateSharpeRatio_nullList_returnsZero() {
        assertThat(riskMetricsService.calculateSharpeRatio(null)).isEqualTo(0.0);
    }

    @Test
    void calculateSharpeRatio_singleElement_returnsZero() {
        assertThat(riskMetricsService.calculateSharpeRatio(List.of(0.01))).isEqualTo(0.0);
    }

    @Test
    void calculateSharpeRatio_allSameReturn_returnsZero() {
        // Std dev = 0 → Sharpe = 0
        List<Double> same = List.of(0.001, 0.001, 0.001, 0.001, 0.001);
        assertThat(riskMetricsService.calculateSharpeRatio(same)).isEqualTo(0.0);
    }

    @Test
    void calculateSharpeRatio_positiveReturns_returnsPositiveValue() {
        List<Double> returns = List.of(0.01, 0.02, 0.015, 0.012, 0.018, 0.011, 0.013, 0.016);
        assertThat(riskMetricsService.calculateSharpeRatio(returns)).isGreaterThan(0.0);
    }

    // ------------------------------------------------------------------
    // calculateCorrelationMatrix
    // ------------------------------------------------------------------

    @Test
    void calculateCorrelationMatrix_diagonalIsOne() {
        double[][] matrix = riskMetricsService.calculateCorrelationMatrix(4);
        for (int i = 0; i < 4; i++) {
            assertThat(matrix[i][i]).isEqualTo(1.0);
        }
    }

    @Test
    void calculateCorrelationMatrix_isSymmetric() {
        double[][] matrix = riskMetricsService.calculateCorrelationMatrix(3);
        assertThat(matrix[0][1]).isEqualTo(matrix[1][0]);
        assertThat(matrix[0][2]).isEqualTo(matrix[2][0]);
        assertThat(matrix[1][2]).isEqualTo(matrix[2][1]);
    }

    @Test
    void calculateCorrelationMatrix_correctDimensions() {
        double[][] matrix = riskMetricsService.calculateCorrelationMatrix(5);
        assertThat(matrix.length).isEqualTo(5);
        for (double[] row : matrix) {
            assertThat(row.length).isEqualTo(5);
        }
    }

    @Test
    void calculateCorrelationMatrix_offDiagonalBetweenZeroAndOne() {
        double[][] matrix = riskMetricsService.calculateCorrelationMatrix(3);
        assertThat(matrix[0][1]).isBetween(0.3, 0.9);
        assertThat(matrix[0][2]).isBetween(0.3, 0.9);
    }

    // ------------------------------------------------------------------
    // generateSimulatedReturns
    // ------------------------------------------------------------------

    @Test
    void generateSimulatedReturns_correctSize() {
        List<Double> returns = riskMetricsService.generateSimulatedReturns(50);
        assertThat(returns).hasSize(50);
    }

    @Test
    void generateSimulatedReturns_isDeterministic() {
        List<Double> first  = riskMetricsService.generateSimulatedReturns(10);
        List<Double> second = riskMetricsService.generateSimulatedReturns(10);
        assertThat(first).isEqualTo(second);
    }

    // ------------------------------------------------------------------
    // getRealReturns
    // ------------------------------------------------------------------

    @Test
    void getRealReturns_delegatesToMarketDataService() {
        List<Double> expected = List.of(0.01, -0.02, 0.03);
        when(marketDataService.getDailyReturns("AAPL", 3)).thenReturn(expected);
        assertThat(riskMetricsService.getRealReturns("AAPL", 3)).isSameAs(expected);
    }

    @Test
    void getRealReturns_serviceFailure_fallsBackToSimulatedData() {
        when(marketDataService.getDailyReturns("AAPL", 30))
                .thenThrow(new RuntimeException("price service down"));
        List<Double> result = riskMetricsService.getRealReturns("AAPL", 30);
        assertThat(result).hasSize(30);
    }
}
