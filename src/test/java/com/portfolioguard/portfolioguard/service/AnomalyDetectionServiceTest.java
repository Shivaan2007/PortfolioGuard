package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock PortfolioService portfolioService;
    @Mock RiskMetricsService riskMetricsService;
    @Mock RestTemplate restTemplate;

    AnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AnomalyDetectionService();
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "riskMetricsService", riskMetricsService);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "riskEngineUrl", "http://localhost:5001");
    }

    @Test
    void detectAnomalies_emptyPortfolio_returnsEarlyMapWithoutCallingEngine() {
        Portfolio p = emptyPortfolio("p1", "u1");
        when(portfolioService.getPortfolio("p1")).thenReturn(p);

        Map<String, Object> result = service.detectAnomalies("p1");

        assertThat(result.get("is_current_anomalous")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("No stocks in portfolio");
    }

    @Test
    void detectAnomalies_mlEngineUnavailable_returnsErrorMap() {
        Portfolio p = portfolioWithStock("p1", "u1", "AAPL");
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(riskMetricsService.getRealReturns(eq("AAPL"), anyInt())).thenReturn(returns(5));
        when(riskMetricsService.getRealReturns(eq("SPY"), anyInt())).thenReturn(returns(5));
        when(riskMetricsService.calculateSharpeRatio(any())).thenReturn(1.0);
        when(riskMetricsService.calculateBeta(any(), any())).thenReturn(1.0);
        when(riskMetricsService.calculateVaR(any(), anyDouble())).thenReturn(-2.5);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("risk engine down"));

        Map<String, Object> result = service.detectAnomalies("p1");

        assertThat(result).containsKey("error");
        assertThat(result.get("is_current_anomalous")).isEqualTo(false);
    }

    @Test
    void detectAnomalies_mlEngineSuccess_returnsEngineResponse() {
        Portfolio p = portfolioWithStock("p1", "u1", "AAPL");
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(riskMetricsService.getRealReturns(eq("AAPL"), anyInt())).thenReturn(returns(5));
        when(riskMetricsService.getRealReturns(eq("SPY"), anyInt())).thenReturn(returns(5));
        when(riskMetricsService.calculateSharpeRatio(any())).thenReturn(0.9);
        when(riskMetricsService.calculateBeta(any(), any())).thenReturn(1.1);
        when(riskMetricsService.calculateVaR(any(), anyDouble())).thenReturn(-3.0);
        Map<String, Object> engineResponse = Map.of("is_current_anomalous", true, "current_score", -0.5);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(engineResponse);

        Map<String, Object> result = service.detectAnomalies("p1");

        assertThat(result.get("is_current_anomalous")).isEqualTo(true);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Portfolio emptyPortfolio(String id, String userId) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setUserId(userId);
        p.setStocks(new ArrayList<>());
        return p;
    }

    private Portfolio portfolioWithStock(String id, String userId, String ticker) {
        Portfolio p = emptyPortfolio(id, userId);
        Stock s = new Stock();
        s.setTicker(ticker);
        s.setCurrentPrice(150.0);
        s.setPurchasePrice(140.0);
        s.setQuantity(10);
        p.getStocks().add(s);
        return p;
    }

    private List<Double> returns(int size) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(0.01 * (i + 1));
        return list;
    }
}
