package com.portfolioguard.portfolioguard.exception;

public class AlphaVantageRateLimitException extends RuntimeException {
    public AlphaVantageRateLimitException() {
        super("Market data is temporarily unavailable. Please try again in a moment.");
    }
}
