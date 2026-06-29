package com.portfolioguard.portfolioguard.dto;

import java.util.List;

public record StockSearchResult(List<Match> matches) {
    public record Match(String symbol, String name, String type, String region, String currency) {}
}
