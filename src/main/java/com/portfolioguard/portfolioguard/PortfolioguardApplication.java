package com.portfolioguard.portfolioguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PortfolioguardApplication {

	public static void main(String[] args) {
		SpringApplication.run(PortfolioguardApplication.class, args);
	}

}
