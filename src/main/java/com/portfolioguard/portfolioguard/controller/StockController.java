package com.portfolioguard.portfolioguard.controller;

import com.portfolioguard.portfolioguard.dto.*;
import com.portfolioguard.portfolioguard.service.SentimentService;
import com.portfolioguard.portfolioguard.service.StockSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    @Autowired
    private StockSearchService stockSearchService;

    @Autowired
    private SentimentService sentimentService;

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(error("Query parameter is required"));
        }
        try {
            return ResponseEntity.ok(stockSearchService.search(query.toUpperCase()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/{ticker}/quote")
    public ResponseEntity<?> quote(@PathVariable String ticker) {
        try {
            return ResponseEntity.ok(stockSearchService.getQuote(ticker.toUpperCase()));
        } catch (com.portfolioguard.portfolioguard.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Invalid ticker: " + ticker));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/{ticker}/overview")
    public ResponseEntity<?> overview(@PathVariable String ticker) {
        try {
            return ResponseEntity.ok(stockSearchService.getOverview(ticker.toUpperCase()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/{ticker}/history")
    public ResponseEntity<?> history(@PathVariable String ticker) {
        try {
            List<Double> closes = stockSearchService.getRecentCloses(ticker.toUpperCase());
            return ResponseEntity.ok(closes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/{ticker}/intelligence")
    public ResponseEntity<?> intelligence(@PathVariable String ticker) {
        try {
            StockIntelligence result = stockSearchService.getIntelligence(ticker.toUpperCase(), sentimentService);
            return ResponseEntity.ok(result);
        } catch (com.portfolioguard.portfolioguard.exception.ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Invalid ticker: " + ticker));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    private java.util.Map<String, String> error(String message) {
        return java.util.Map.of("error", message);
    }
}
