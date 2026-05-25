package com.portfolioguard.portfolioguard.repository;

import com.portfolioguard.portfolioguard.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, String> {

    List<Portfolio> findByUserId(String userId);

    boolean existsByNameAndUserId(String name, String userId);
}