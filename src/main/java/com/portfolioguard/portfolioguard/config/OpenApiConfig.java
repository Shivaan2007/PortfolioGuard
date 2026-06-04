package com.portfolioguard.portfolioguard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI portfolioGuardOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PortfolioGuard API")
                        .description("Institutional portfolio risk monitoring system — real-time risk metrics, anomaly detection, and sentiment analysis")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PortfolioGuard")
                                .email("admin@portfolioguard.com")));
    }
}
