package com.portfolioguard.portfolioguard.repository;

import com.portfolioguard.portfolioguard.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, String> {

    List<Stock> findByPortfolioId(String portfolioId);

    Optional<Stock> findByTickerAndPortfolioId(String ticker, String portfolioId);

    boolean existsByTickerAndPortfolioId(String ticker, String portfolioId);
}