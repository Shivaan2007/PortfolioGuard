package com.portfolioguard.portfolioguard.dto;

public record StockQuote(
    String symbol,
    double price,
    double change,
    double changePercent,
    double open,
    double high,
    double low,
    double previousClose,
    long volume,
    String latestTradingDay
) {}
