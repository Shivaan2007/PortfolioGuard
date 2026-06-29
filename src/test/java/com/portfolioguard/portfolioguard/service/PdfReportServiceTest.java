package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfReportServiceTest {

    @Mock PortfolioService portfolioService;
    @Mock RiskMetricsService riskMetricsService;
    @Mock SentimentService sentimentService;

    @InjectMocks PdfReportService pdfReportService;

    @Test
    void generateReport_withStocks_returnsNonEmptyPdfBytes() throws Exception {
        Portfolio p = portfolioWithStocks("p1", "Tech Fund", "u1",
                stock("AAPL", 150.0, 140.0, 10),
                stock("TSLA", 200.0, 180.0, 5));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(portfolioService.calculatePortfolioValue("p1")).thenReturn(2500.0);
        when(portfolioService.calculatePnL("p1")).thenReturn(150.0);
        when(portfolioService.calculateTotalReturn("p1")).thenReturn(6.4);
        when(portfolioService.calculateSharpeRatio("p1")).thenReturn(0.85);
        when(riskMetricsService.getRealReturns(eq("AAPL"), anyInt())).thenReturn(simReturns(100));
        when(riskMetricsService.getRealReturns(eq("SPY"), anyInt())).thenReturn(simReturns(100));
        when(riskMetricsService.calculateVaR(any(), eq(0.95))).thenReturn(-2.5);
        when(riskMetricsService.calculateVaR(any(), eq(0.99))).thenReturn(-3.8);
        when(riskMetricsService.calculateBeta(any(), any())).thenReturn(1.2);
        Map<String, Object> sentiment = new HashMap<>();
        sentiment.put("label", "POSITIVE");
        sentiment.put("score", 0.75);
        when(sentimentService.getSentiment(anyString())).thenReturn(sentiment);

        byte[] pdf = pdfReportService.generateReport("p1");

        assertThat(pdf).isNotNull().isNotEmpty();
        // PDF files start with the magic bytes %PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateReport_emptyPortfolio_doesNotThrow() {
        Portfolio p = portfolioWithStocks("p1", "Empty Fund", "u1");
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(portfolioService.calculatePortfolioValue("p1")).thenReturn(0.0);
        when(portfolioService.calculatePnL("p1")).thenReturn(0.0);
        when(portfolioService.calculateTotalReturn("p1")).thenReturn(0.0);
        when(portfolioService.calculateSharpeRatio("p1")).thenReturn(0.0);

        assertThatCode(() -> pdfReportService.generateReport("p1")).doesNotThrowAnyException();
    }

    @Test
    void generateReport_emptyPortfolio_stillReturnsPdfBytes() throws Exception {
        Portfolio p = portfolioWithStocks("p1", "Empty Fund", "u1");
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(portfolioService.calculatePortfolioValue(any())).thenReturn(0.0);
        when(portfolioService.calculatePnL(any())).thenReturn(0.0);
        when(portfolioService.calculateTotalReturn(any())).thenReturn(0.0);
        when(portfolioService.calculateSharpeRatio(any())).thenReturn(0.0);

        byte[] pdf = pdfReportService.generateReport("p1");
        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateReport_sentimentServiceFails_stillGeneratesPdf() throws Exception {
        Portfolio p = portfolioWithStocks("p1", "Tech Fund", "u1", stock("AAPL", 150, 140, 10));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(portfolioService.calculatePortfolioValue(any())).thenReturn(1500.0);
        when(portfolioService.calculatePnL(any())).thenReturn(100.0);
        when(portfolioService.calculateTotalReturn(any())).thenReturn(7.1);
        when(portfolioService.calculateSharpeRatio(any())).thenReturn(0.5);
        when(riskMetricsService.getRealReturns(eq("AAPL"), anyInt())).thenReturn(simReturns(100));
        when(riskMetricsService.getRealReturns(eq("SPY"), anyInt())).thenReturn(simReturns(100));
        when(riskMetricsService.calculateVaR(any(), anyDouble())).thenReturn(-2.0);
        when(riskMetricsService.calculateBeta(any(), any())).thenReturn(1.1);
        when(sentimentService.getSentiment("AAPL")).thenThrow(new RuntimeException("unavailable"));

        byte[] pdf = pdfReportService.generateReport("p1");
        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    void generateReport_riskMetricsThrows_stillGeneratesPdf() throws Exception {
        Portfolio p = portfolioWithStocks("p1", "Tech Fund", "u1", stock("AAPL", 150, 140, 10));
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(portfolioService.calculatePortfolioValue(any())).thenReturn(1500.0);
        when(portfolioService.calculatePnL(any())).thenReturn(100.0);
        when(portfolioService.calculateTotalReturn(any())).thenReturn(7.1);
        when(portfolioService.calculateSharpeRatio(any())).thenReturn(0.5);
        when(riskMetricsService.getRealReturns(anyString(), anyInt()))
                .thenThrow(new RuntimeException("price service down"));
        Map<String, Object> sentiment = new HashMap<>();
        sentiment.put("label", "NEUTRAL");
        sentiment.put("score", 0.0);
        when(sentimentService.getSentiment(anyString())).thenReturn(sentiment);

        // Risk section falls back to "Risk metrics temporarily unavailable."
        assertThatCode(() -> pdfReportService.generateReport("p1")).doesNotThrowAnyException();
    }

    @Test
    void generateReport_doesNotIncludeUserId() throws Exception {
        Portfolio p = portfolioWithStocks("p1", "Tech Fund", "u1");
        p.setUserId("internal-uuid-1234");
        when(portfolioService.getPortfolio("p1")).thenReturn(p);
        when(portfolioService.calculatePortfolioValue(any())).thenReturn(0.0);
        when(portfolioService.calculatePnL(any())).thenReturn(0.0);
        when(portfolioService.calculateTotalReturn(any())).thenReturn(0.0);
        when(portfolioService.calculateSharpeRatio(any())).thenReturn(0.0);

        byte[] pdf = pdfReportService.generateReport("p1");
        String content = new String(pdf);
        // The internal user ID must NOT appear in the PDF
        assertThat(content).doesNotContain("internal-uuid-1234");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Portfolio portfolioWithStocks(String id, String name, String userId, Stock... stocks) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setName(name);
        p.setUserId(userId);
        p.setStrategy("Growth");
        p.setDescription("Test portfolio");
        p.setStocks(new ArrayList<>(List.of(stocks)));
        // Set createdAt via reflection to avoid @PrePersist not being called in tests
        try {
            var field = Portfolio.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(p, LocalDateTime.now());
        } catch (Exception ignored) {}
        return p;
    }

    private Stock stock(String ticker, double current, double purchase, int qty) {
        Stock s = new Stock();
        s.setTicker(ticker);
        s.setCurrentPrice(current);
        s.setPurchasePrice(purchase);
        s.setQuantity(qty);
        s.setSector("Technology");
        return s;
    }

    private List<Double> simReturns(int size) {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(0.001 * (i % 5 - 2));
        return list;
    }
}
