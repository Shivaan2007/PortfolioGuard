package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.exception.ForbiddenException;
import com.portfolioguard.portfolioguard.exception.ResourceNotFoundException;
import com.portfolioguard.portfolioguard.kafka.PriceEventProducer;
import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import com.portfolioguard.portfolioguard.repository.PortfolioRepository;
import com.portfolioguard.portfolioguard.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock StockRepository stockRepository;
    @Mock MarketDataService marketDataService;
    @Mock PriceEventProducer priceEventProducer;

    @InjectMocks PortfolioService portfolioService;

    // ------------------------------------------------------------------
    // createPortfolio
    // ------------------------------------------------------------------

    @Test
    void createPortfolio_success_savesAndReturns() {
        when(portfolioRepository.existsByNameAndUserId("Tech Fund", "u1")).thenReturn(false);
        Portfolio saved = portfolio("p1", "Tech Fund", "u1");
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(saved);

        Portfolio result = portfolioService.createPortfolio("Tech Fund", "desc", "growth", "u1");
        assertThat(result.getName()).isEqualTo("Tech Fund");
    }

    @Test
    void createPortfolio_duplicateName_throwsIllegalArgument() {
        when(portfolioRepository.existsByNameAndUserId("Tech Fund", "u1")).thenReturn(true);
        assertThatThrownBy(() -> portfolioService.createPortfolio("Tech Fund", "desc", "growth", "u1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    // ------------------------------------------------------------------
    // getPortfolio
    // ------------------------------------------------------------------

    @Test
    void getPortfolio_found_returnsPortfolio() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        assertThat(portfolioService.getPortfolio("p1")).isSameAs(p);
    }

    @Test
    void getPortfolio_notFound_throwsResourceNotFoundException() {
        when(portfolioRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> portfolioService.getPortfolio("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // getPortfolioForUser
    // ------------------------------------------------------------------

    @Test
    void getPortfolioForUser_ownerAccess_returnsPortfolio() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        assertThat(portfolioService.getPortfolioForUser("p1", "u1")).isSameAs(p);
    }

    @Test
    void getPortfolioForUser_wrongUser_throwsForbidden() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> portfolioService.getPortfolioForUser("p1", "attacker"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getPortfolioForUser_notFound_throwsResourceNotFoundException() {
        when(portfolioRepository.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> portfolioService.getPortfolioForUser("x", "u1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // addStock / removeStock
    // ------------------------------------------------------------------

    @Test
    void addStock_success_addsStockAndSaves() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        when(stockRepository.existsByTickerAndPortfolioId("AAPL", "p1")).thenReturn(false);
        when(portfolioRepository.save(p)).thenReturn(p);

        Stock stock = stock("AAPL", 150.0, 140.0, 10);
        Portfolio result = portfolioService.addStock("p1", stock);

        assertThat(result.getStocks()).contains(stock);
    }

    @Test
    void addStock_duplicate_throwsIllegalArgument() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        when(stockRepository.existsByTickerAndPortfolioId("AAPL", "p1")).thenReturn(true);

        assertThatThrownBy(() -> portfolioService.addStock("p1", stock("AAPL", 150, 140, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAPL");
    }

    @Test
    void removeStock_removesMatchingTickerAndSaves() {
        Stock aapl = stock("AAPL", 150, 140, 10);
        Stock tsla = stock("TSLA", 200, 180, 5);
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().addAll(List.of(aapl, tsla));
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        when(portfolioRepository.save(p)).thenReturn(p);

        Portfolio result = portfolioService.removeStock("p1", "AAPL");

        assertThat(result.getStocks()).doesNotContain(aapl);
        assertThat(result.getStocks()).contains(tsla);
    }

    // ------------------------------------------------------------------
    // Calculated metrics
    // ------------------------------------------------------------------

    @Test
    void calculatePortfolioValue_sumsCurrentPriceTimesQuantity() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().add(stock("AAPL", 150.0, 140.0, 10)); // 1500
        p.getStocks().add(stock("TSLA", 200.0, 180.0, 5));  // 1000
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));

        assertThat(portfolioService.calculatePortfolioValue("p1")).isEqualTo(2500.0);
    }

    @Test
    void calculatePnL_sumsGainPerShare() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().add(stock("AAPL", 150.0, 140.0, 10)); // (150-140)*10 = 100
        p.getStocks().add(stock("TSLA", 200.0, 210.0, 5));  // (200-210)*5 = -50
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));

        assertThat(portfolioService.calculatePnL("p1")).isEqualTo(50.0);
    }

    @Test
    void calculateTotalReturn_correctPercentage() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().add(stock("AAPL", 110.0, 100.0, 10)); // current=1100, init=1000 → 10%
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));

        assertThat(portfolioService.calculateTotalReturn("p1")).isEqualTo(10.0);
    }

    @Test
    void calculateTotalReturn_zeroInitialValue_returnsZero() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().add(stock("AAPL", 110.0, 0.0, 10));
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));

        assertThat(portfolioService.calculateTotalReturn("p1")).isEqualTo(0.0);
    }

    @Test
    void calculateSharpeRatio_returnsResult() {
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().add(stock("AAPL", 110.0, 100.0, 10));
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        // Sharpe = (totalReturn - riskFreeRate) / mockVolatility = (10 - 2) / 15 ≈ 0.533
        double sharpe = portfolioService.calculateSharpeRatio("p1");
        assertThat(sharpe).isCloseTo(8.0 / 15.0, org.assertj.core.data.Offset.offset(0.001));
    }

    // ------------------------------------------------------------------
    // refreshAndPublishPrices
    // ------------------------------------------------------------------

    @Test
    void refreshAndPublishPrices_updatesAllStocksAndSaves() {
        Stock aapl = stock("AAPL", 140.0, 130.0, 10);
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().add(aapl);
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        when(marketDataService.getCurrentPrice("AAPL")).thenReturn(150.0);
        when(portfolioRepository.save(p)).thenReturn(p);

        portfolioService.refreshAndPublishPrices("p1");

        assertThat(aapl.getCurrentPrice()).isEqualTo(150.0);
        verify(priceEventProducer).publishPriceUpdate("AAPL", 140.0, 150.0, "p1");
        verify(portfolioRepository).save(p);
    }

    @Test
    void refreshAndPublishPrices_priceServiceFailure_continuesOtherStocksAndSaves() {
        Stock aapl = stock("AAPL", 140.0, 130.0, 10);
        Stock tsla = stock("TSLA", 200.0, 180.0, 5);
        Portfolio p = portfolio("p1", "Fund", "u1");
        p.getStocks().addAll(List.of(aapl, tsla));
        when(portfolioRepository.findById("p1")).thenReturn(Optional.of(p));
        when(marketDataService.getCurrentPrice("AAPL")).thenThrow(new RuntimeException("unavailable"));
        when(marketDataService.getCurrentPrice("TSLA")).thenReturn(210.0);
        when(portfolioRepository.save(p)).thenReturn(p);

        // Should not throw
        portfolioService.refreshAndPublishPrices("p1");

        assertThat(tsla.getCurrentPrice()).isEqualTo(210.0);
        verify(priceEventProducer, never()).publishPriceUpdate(eq("AAPL"), anyDouble(), anyDouble(), anyString());
        verify(priceEventProducer).publishPriceUpdate("TSLA", 200.0, 210.0, "p1");
        verify(portfolioRepository).save(p);
    }

    @Test
    void getUserPortfolios_delegatesToRepository() {
        List<Portfolio> portfolios = List.of(portfolio("p1", "Fund", "u1"));
        when(portfolioRepository.findByUserId("u1")).thenReturn(portfolios);
        assertThat(portfolioService.getUserPortfolios("u1")).isSameAs(portfolios);
    }

    @Test
    void deletePortfolio_callsRepositoryDeleteById() {
        portfolioService.deletePortfolio("p1");
        verify(portfolioRepository).deleteById("p1");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Portfolio portfolio(String id, String name, String userId) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setName(name);
        p.setUserId(userId);
        p.setStocks(new ArrayList<>());
        return p;
    }

    private Stock stock(String ticker, double currentPrice, double purchasePrice, int qty) {
        Stock s = new Stock();
        s.setTicker(ticker);
        s.setCurrentPrice(currentPrice);
        s.setPurchasePrice(purchasePrice);
        s.setQuantity(qty);
        return s;
    }
}
