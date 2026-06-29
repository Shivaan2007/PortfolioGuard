package com.portfolioguard.portfolioguard.dto;

import java.util.List;

public record StockIntelligence(
    String symbol,
    StockQuote quote,
    StockOverview overview,
    List<String> signals,      // e.g. "Momentum Positive", "Near 52-Week High"
    String riskLevel,          // LOW, MODERATE, HIGH, CRITICAL
    String trend,              // UPWARD, DOWNWARD, SIDEWAYS
    String sentimentLabel,     // POSITIVE, NEUTRAL, NEGATIVE, or null if unavailable
    Double sentimentScore,
    String disclaimer
) {}
