package com.portfolioguard.portfolioguard.controller;

import com.portfolioguard.portfolioguard.dto.StockQuote;
import com.portfolioguard.portfolioguard.dto.StockSearchResult;
import com.portfolioguard.portfolioguard.dto.StockOverview;
import com.portfolioguard.portfolioguard.dto.StockIntelligence;
import com.portfolioguard.portfolioguard.exception.ResourceNotFoundException;
import com.portfolioguard.portfolioguard.service.SentimentService;
import com.portfolioguard.portfolioguard.service.StockSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StockControllerTest {

    @Mock StockSearchService stockSearchService;
    @Mock SentimentService sentimentService;
    @InjectMocks StockController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    @Test
    void search_success_returns200() throws Exception {
        StockSearchResult result = new StockSearchResult(List.of());
        when(stockSearchService.search("AAPL")).thenReturn(result);

        mockMvc.perform(get("/api/stocks/search").param("query", "aapl"))
                .andExpect(status().isOk());
    }

    @Test
    void search_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/stocks/search").param("query", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void search_serviceThrows_returns503() throws Exception {
        when(stockSearchService.search(anyString())).thenThrow(new RuntimeException("rate limited"));

        mockMvc.perform(get("/api/stocks/search").param("query", "AAPL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("rate limited"));
    }

    // ------------------------------------------------------------------
    // quote
    // ------------------------------------------------------------------

    @Test
    void quote_success_returns200() throws Exception {
        StockQuote quote = new StockQuote("AAPL", 150.0, 1.5, 1.0, 149.0, 152.0, 148.0, 148.5, 50_000_000L, "2024-01-01");
        when(stockSearchService.getQuote("AAPL")).thenReturn(quote);

        mockMvc.perform(get("/api/stocks/AAPL/quote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.price").value(150.0));
    }

    @Test
    void quote_notFound_returns404() throws Exception {
        when(stockSearchService.getQuote("FAKE")).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/stocks/FAKE/quote"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void quote_serviceError_returns503() throws Exception {
        when(stockSearchService.getQuote("AAPL")).thenThrow(new RuntimeException("service down"));

        mockMvc.perform(get("/api/stocks/AAPL/quote"))
                .andExpect(status().isServiceUnavailable());
    }

    // ------------------------------------------------------------------
    // overview
    // ------------------------------------------------------------------

    @Test
    void overview_success_returns200() throws Exception {
        StockOverview overview = new StockOverview("AAPL", "Apple Inc.", "Technology",
                "Consumer Electronics", "Apple makes iPhones",
                3_000_000_000_000.0, 30.0, 6.0, 0.5, 1.2, 200.0, 150.0);
        when(stockSearchService.getOverview("AAPL")).thenReturn(overview);

        mockMvc.perform(get("/api/stocks/AAPL/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"));
    }

    @Test
    void overview_serviceError_returns503() throws Exception {
        when(stockSearchService.getOverview("AAPL")).thenThrow(new RuntimeException("timeout"));

        mockMvc.perform(get("/api/stocks/AAPL/overview"))
                .andExpect(status().isServiceUnavailable());
    }

    // ------------------------------------------------------------------
    // history
    // ------------------------------------------------------------------

    @Test
    void history_success_returns200() throws Exception {
        when(stockSearchService.getRecentCloses("AAPL")).thenReturn(List.of(155.0, 153.0, 151.0));

        mockMvc.perform(get("/api/stocks/AAPL/history"))
                .andExpect(status().isOk());
    }

    @Test
    void history_serviceError_returns503() throws Exception {
        when(stockSearchService.getRecentCloses("AAPL")).thenThrow(new RuntimeException("unavailable"));

        mockMvc.perform(get("/api/stocks/AAPL/history"))
                .andExpect(status().isServiceUnavailable());
    }

    // ------------------------------------------------------------------
    // intelligence
    // ------------------------------------------------------------------

    @Test
    void intelligence_success_returns200() throws Exception {
        StockQuote quote = new StockQuote("AAPL", 150.0, 1.5, 1.0, 149.0, 152.0, 148.0, 148.5, 50_000_000L, "2024-01-01");
        StockOverview overview = new StockOverview("AAPL", "Apple Inc.", "Technology",
                "Consumer Electronics", "desc", 3_000_000_000_000.0, 30.0, 6.0, 0.5, 1.2, 200.0, 150.0);
        StockIntelligence intelligence = new StockIntelligence("AAPL", quote, overview,
                List.of("Momentum Positive"), "LOW", "UPWARD", "POSITIVE", 0.8,
                "For informational purposes only.");
        when(stockSearchService.getIntelligence("AAPL", sentimentService)).thenReturn(intelligence);

        mockMvc.perform(get("/api/stocks/AAPL/intelligence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.riskLevel").value("LOW"));
    }

    @Test
    void intelligence_notFound_returns404() throws Exception {
        when(stockSearchService.getIntelligence(anyString(), any())).thenThrow(new ResourceNotFoundException("ticker not found"));

        mockMvc.perform(get("/api/stocks/FAKE/intelligence"))
                .andExpect(status().isNotFound());
    }

    @Test
    void intelligence_serviceError_returns503() throws Exception {
        when(stockSearchService.getIntelligence(anyString(), any())).thenThrow(new RuntimeException("timeout"));

        mockMvc.perform(get("/api/stocks/AAPL/intelligence"))
                .andExpect(status().isServiceUnavailable());
    }
}
