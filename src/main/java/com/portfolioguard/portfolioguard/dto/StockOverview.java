package com.portfolioguard.portfolioguard.dto;

public record StockOverview(
    String symbol,
    String name,
    String sector,
    String industry,
    String description,
    Double marketCap,
    Double peRatio,
    Double eps,
    Double dividendYield,
    Double beta,
    Double week52High,
    Double week52Low
) {}
