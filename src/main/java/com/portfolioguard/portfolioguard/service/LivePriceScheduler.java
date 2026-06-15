package com.portfolioguard.portfolioguard.service;

import com.portfolioguard.portfolioguard.model.Portfolio;
import com.portfolioguard.portfolioguard.model.Stock;
import com.portfolioguard.portfolioguard.repository.PortfolioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class LivePriceScheduler {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Random random = new Random();

    // Simulates realistic market tick every 5 seconds
    @Scheduled(fixedRate = 5000)
    public void broadcastLivePrices() {
        List<Portfolio> portfolios = portfolioRepository.findAll();

        for (Portfolio portfolio : portfolios) {
            Map<String, Object> priceUpdate = new HashMap<>();
            Map<String, Object> positions = new HashMap<>();

            double totalValue = 0;
            double totalCost = 0;

            for (Stock stock : portfolio.getStocks()) {
                // Simulate realistic price movement: ±0.05% to ±0.3% per tick
                double volatility = 0.001 + random.nextDouble() * 0.002;
                double direction = random.nextDouble() > 0.5 ? 1 : -1;
                double change = stock.getCurrentPrice() * volatility * direction;
                double newPrice = Math.round((stock.getCurrentPrice() + change) * 100.0) / 100.0;

                // Keep price within 20% of purchase price for realism
                double minPrice = stock.getPurchasePrice() * 0.80;
                double maxPrice = stock.getPurchasePrice() * 1.50;
                newPrice = Math.max(minPrice, Math.min(maxPrice, newPrice));

                double pnl = (newPrice - stock.getPurchasePrice()) * stock.getQuantity();
                double pnlPct = ((newPrice - stock.getPurchasePrice()) / stock.getPurchasePrice()) * 100;

                Map<String, Object> tickerData = new HashMap<>();
                tickerData.put("ticker", stock.getTicker());
                tickerData.put("price", newPrice);
                tickerData.put("change", Math.round(change * 100.0) / 100.0);
                tickerData.put("changePct", Math.round((change / stock.getCurrentPrice()) * 10000.0) / 100.0);
                tickerData.put("pnl", Math.round(pnl * 100.0) / 100.0);
                tickerData.put("pnlPct", Math.round(pnlPct * 100.0) / 100.0);
                tickerData.put("quantity", stock.getQuantity());
                tickerData.put("purchasePrice", stock.getPurchasePrice());

                positions.put(stock.getTicker(), tickerData);

                totalValue += newPrice * stock.getQuantity();
                totalCost += stock.getPurchasePrice() * stock.getQuantity();
            }

            double totalPnl = totalValue - totalCost;
            double totalReturn = totalCost > 0 ? ((totalValue - totalCost) / totalCost) * 100 : 0;

            priceUpdate.put("portfolioId", portfolio.getId());
            priceUpdate.put("portfolioName", portfolio.getName());
            priceUpdate.put("positions", positions);
            priceUpdate.put("totalValue", Math.round(totalValue * 100.0) / 100.0);
            priceUpdate.put("totalPnl", Math.round(totalPnl * 100.0) / 100.0);
            priceUpdate.put("totalReturn", Math.round(totalReturn * 100.0) / 100.0);
            priceUpdate.put("timestamp", LocalDateTime.now().toString());

            messagingTemplate.convertAndSend(
                    "/topic/prices/" + portfolio.getId(), priceUpdate);
        }
    }
}
