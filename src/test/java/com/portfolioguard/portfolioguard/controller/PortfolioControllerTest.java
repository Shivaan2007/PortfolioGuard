package com.portfolioguard.portfolioguard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import com.portfolioguard.portfolioguard.model.User;
import com.portfolioguard.portfolioguard.security.UserPrincipal;
import com.portfolioguard.portfolioguard.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock PortfolioService portfolioService;
    @Mock RiskMetricsService riskMetricsService;
    @Mock MarketDataService marketDataService;
    @Mock CorrelationMonitorService correlationMonitorService;
    @Mock AnomalyDetectionService anomalyDetectionService;
    @Mock RiskAlertService riskAlertService;
    @Mock SentimentService sentimentService;
    @Mock PdfReportService pdfReportService;

    @InjectMocks PortfolioController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        setPrincipal("u1");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // health
    // ------------------------------------------------------------------

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/portfolios/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("PortfolioGuard is running"));
    }

    // ------------------------------------------------------------------
    // getAllPortfolios
    // ------------------------------------------------------------------

    @Test
    void getAllPortfolios_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        mockMvc.perform(get("/api/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p1"));
    }

    // ------------------------------------------------------------------
    // createPortfolio
    // ------------------------------------------------------------------

    @Test
    void createPortfolio_returns201() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.createPortfolio(eq("Tech Fund"), anyString(), anyString(), eq("u1")))
                .thenReturn(p);

        String body = """
            {"name":"Tech Fund","description":"My tech portfolio","strategy":"Growth"}
            """;

        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("p1"))
                .andExpect(jsonPath("$.name").value("Tech Fund"));
    }

    // ------------------------------------------------------------------
    // getPortfolio
    // ------------------------------------------------------------------

    @Test
    void getPortfolio_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);

        mockMvc.perform(get("/api/portfolios/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p1"));
    }

    // ------------------------------------------------------------------
    // addStock
    // ------------------------------------------------------------------

    @Test
    void addStock_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        when(portfolioService.addStock(eq("p1"), any(Stock.class))).thenReturn(p);

        String body = """
            {"ticker":"AAPL","currentPrice":150.0,"purchasePrice":140.0,"quantity":10,"sector":"Technology"}
            """;

        mockMvc.perform(post("/api/portfolios/p1/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // removeStock
    // ------------------------------------------------------------------

    @Test
    void removeStock_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        when(portfolioService.removeStock("p1", "AAPL")).thenReturn(p);

        mockMvc.perform(delete("/api/portfolios/p1/stocks/AAPL"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // deletePortfolio
    // ------------------------------------------------------------------

    @Test
    void deletePortfolio_returns204() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        doNothing().when(portfolioService).deletePortfolio("p1");

        mockMvc.perform(delete("/api/portfolios/p1"))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // getAnalytics
    // ------------------------------------------------------------------

    @Test
    void getAnalytics_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        when(portfolioService.calculatePortfolioValue("p1")).thenReturn(10000.0);
        when(portfolioService.calculatePnL("p1")).thenReturn(500.0);
        when(portfolioService.calculateTotalReturn("p1")).thenReturn(5.0);
        when(portfolioService.calculateSharpeRatio("p1")).thenReturn(1.2);

        mockMvc.perform(get("/api/portfolios/p1/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioValue").value(10000.0))
                .andExpect(jsonPath("$.sharpeRatio").value(1.2));
    }

    // ------------------------------------------------------------------
    // getRiskMetrics
    // ------------------------------------------------------------------

    @Test
    void getRiskMetrics_withStocks_returns200() throws Exception {
        Portfolio p = portfolioWithStock("p1", "AAPL");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        when(riskMetricsService.getRealReturns(anyString(), anyInt())).thenReturn(List.of(0.01, -0.02, 0.03));
        when(riskMetricsService.calculateVaR(any(), anyDouble())).thenReturn(-2.5);
        when(riskMetricsService.calculateBeta(any(), any())).thenReturn(1.1);
        when(riskMetricsService.calculateSharpeRatio(any())).thenReturn(0.8);
        when(riskMetricsService.calculateCorrelationMatrix(anyInt())).thenReturn(new double[][]{{1.0}});

        mockMvc.perform(get("/api/portfolios/p1/risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beta").value(1.1))
                .andExpect(jsonPath("$.stockCount").value(1));
    }

    @Test
    void getRiskMetrics_emptyPortfolio_returns400() throws Exception {
        Portfolio p = portfolio("p1", "Empty Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);

        mockMvc.perform(get("/api/portfolios/p1/risk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ------------------------------------------------------------------
    // correlations/alerts
    // ------------------------------------------------------------------

    @Test
    void getCorrelationAlerts_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        when(correlationMonitorService.detectCorrelationBreakdowns(p)).thenReturn(List.of());

        mockMvc.perform(get("/api/portfolios/p1/correlations/alerts"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // anomalies
    // ------------------------------------------------------------------

    @Test
    void getAnomalies_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        Map<String, Object> result = Map.of("status", "ok");
        when(anomalyDetectionService.detectAnomalies("p1")).thenReturn(result);

        mockMvc.perform(get("/api/portfolios/p1/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ------------------------------------------------------------------
    // refresh
    // ------------------------------------------------------------------

    @Test
    void refreshPrices_returns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        doNothing().when(portfolioService).refreshAndPublishPrices("p1");

        mockMvc.perform(post("/api/portfolios/p1/refresh"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshPortfolio_checksAlertsAndReturns200() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        doNothing().when(portfolioService).refreshAndPublishPrices("p1");
        doNothing().when(riskAlertService).checkAndBroadcastAlerts("p1");

        mockMvc.perform(post("/api/portfolios/p1/refresh-prices"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // sentiment
    // ------------------------------------------------------------------

    @Test
    void getSentiment_returns200() throws Exception {
        Portfolio p = portfolioWithStock("p1", "AAPL");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        when(sentimentService.getSentiment("AAPL")).thenReturn(Map.of("label", "POSITIVE", "score", 0.8));

        mockMvc.perform(get("/api/portfolios/p1/sentiment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AAPL.label").value("POSITIVE"));
    }

    // ------------------------------------------------------------------
    // report
    // ------------------------------------------------------------------

    @Test
    void generateReport_returnsPdfContentType() throws Exception {
        Portfolio p = portfolio("p1", "Tech Fund", "u1");
        when(portfolioService.getPortfolioForUser("p1", "u1")).thenReturn(p);
        byte[] pdfBytes = new byte[]{37, 80, 68, 70};  // %PDF
        when(pdfReportService.generateReport("p1")).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/portfolios/p1/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setPrincipal(String userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");
        user.setEmail("test@test.com");
        user.setRole("USER");
        UserPrincipal principal = new UserPrincipal(user);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private Portfolio portfolio(String id, String name, String userId) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setName(name);
        p.setUserId(userId);
        p.setStocks(new ArrayList<>());
        return p;
    }

    private Portfolio portfolioWithStock(String id, String ticker) {
        Portfolio p = portfolio(id, "Fund", "u1");
        Stock s = new Stock();
        s.setTicker(ticker);
        s.setCurrentPrice(150.0);
        s.setPurchasePrice(140.0);
        s.setQuantity(10);
        p.getStocks().add(s);
        return p;
    }
}
