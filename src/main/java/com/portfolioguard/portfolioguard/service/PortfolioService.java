package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.kafka.PriceEventProducer;
import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import com.portfolioguard.portfolioguard.repository.PortfolioRepository;
import com.portfolioguard.portfolioguard.repository.StockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PortfolioService {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private PriceEventProducer priceEventProducer;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private StockRepository stockRepository;

    public Portfolio createPortfolio(String name, String description,
                                     String strategy, String userId) {
        if (portfolioRepository.existsByNameAndUserId(name, userId)) {
            throw new RuntimeException("Portfolio with this name already exists");
        }
        Portfolio portfolio = new Portfolio();
        portfolio.setName(name);
        portfolio.setDescription(description);
        portfolio.setStrategy(strategy);
        portfolio.setUserId(userId);
        return portfolioRepository.save(portfolio);
    }

    public Portfolio getPortfolio(String id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Portfolio not found"));
    }

    public List<Portfolio> getAllPortfolios() {
        return portfolioRepository.findAll();
    }

    public List<Portfolio> getUserPortfolios(String userId) {
        return portfolioRepository.findByUserId(userId);
    }

    public Portfolio addStock(String portfolioId, Stock stock) {
        Portfolio portfolio = getPortfolio(portfolioId);
        if (stockRepository.existsByTickerAndPortfolioId(stock.getTicker(), portfolioId)) {
            throw new RuntimeException("Stock already exists in portfolio");
        }

        portfolio.getStocks().add(stock);
        return portfolioRepository.save(portfolio);
    }

    public Portfolio removeStock(String portfolioId, String ticker) {
        Portfolio portfolio = getPortfolio(portfolioId);
        portfolio.getStocks().removeIf(s -> s.getTicker().equals(ticker));
        return portfolioRepository.save(portfolio);
    }

    public void deletePortfolio(String id) {
        portfolioRepository.deleteById(id);
    }

    public double calculatePortfolioValue(String portfolioId) {
        Portfolio portfolio = getPortfolio(portfolioId);
        double totalValue = 0;

        for (Stock stock : portfolio.getStocks()) {
            totalValue += stock.getCurrentPrice() * stock.getQuantity();
        }
        return totalValue;
    }

    public double calculatePnL(String portfolioId) {
        Portfolio portfolio = getPortfolio(portfolioId);
        double pnl = 0;

        for (Stock stock : portfolio.getStocks()) {
            pnl += (stock.getCurrentPrice() - stock.getPurchasePrice()) * stock.getQuantity();

        }
        return pnl;
    }

    public double calculateTotalReturn(String portfolioId) {
        Portfolio portfolio = getPortfolio(portfolioId);
        double currentValue = 0;
        double initialValue = 0;

        for (Stock stock : portfolio.getStocks()) {
            currentValue += stock.getCurrentPrice() * stock.getQuantity();
            initialValue += stock.getPurchasePrice() * stock.getQuantity();
        }

        if (initialValue == 0) {
            return 0;
        }

        return ((currentValue - initialValue) / initialValue) * 100;
    }


    public double calculateSharpeRatio(String portfolioId) {

        double totalReturn =
                calculateTotalReturn(portfolioId);

        double riskFreeRate = 2.0;

        double mockVolatility = 15.0;

        return (totalReturn - riskFreeRate)
                / mockVolatility;
    }


    public void refreshAndPublishPrices(String portfolioId) {

        Portfolio portfolio = getPortfolio(portfolioId);

        for (Stock stock : portfolio.getStocks()) {

            try {

                double oldPrice = stock.getCurrentPrice();

                double newPrice =
                        marketDataService.getCurrentPrice(
                                stock.getTicker()
                        );

                stock.setCurrentPrice(newPrice);

                priceEventProducer.publishPriceUpdate(
                        stock.getTicker(),
                        oldPrice,
                        newPrice,
                        portfolioId
                );

            } catch (Exception e) {

                System.out.println(
                        "Failed to refresh price for: "
                                + stock.getTicker()
                );
            }
        }

        portfolioRepository.save(portfolio);
    }

}






