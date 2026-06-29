package com.portfolioguard.portfolioguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioguard.portfolioguard.dto.*;
import com.portfolioguard.portfolioguard.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockSearchService {

    @Value("${alphavantage.api.key}")
    private String apiKey;

    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // SYMBOL_SEARCH — search by ticker or company keyword
    // ------------------------------------------------------------------
    @Cacheable(value = "stockSearch", key = "#query")
    public StockSearchResult search(String query) {
        String url = BASE_URL + "?function=SYMBOL_SEARCH&keywords=" + query + "&apikey=" + apiKey;
        JsonNode root = fetchJson(url);

        List<StockSearchResult.Match> matches = new ArrayList<>();
        JsonNode bestMatches = root.path("bestMatches");
        if (bestMatches.isArray()) {
            for (JsonNode m : bestMatches) {
                matches.add(new StockSearchResult.Match(
                        text(m, "1. symbol"),
                        text(m, "2. name"),
                        text(m, "3. type"),
                        text(m, "4. region"),
                        text(m, "8. currency")
                ));
            }
        }
        return new StockSearchResult(matches);
    }

    // ------------------------------------------------------------------
    // GLOBAL_QUOTE — live price snapshot
    // ------------------------------------------------------------------
    @Cacheable(value = "stockQuote", key = "#ticker")
    public StockQuote getQuote(String ticker) {
        String url = BASE_URL + "?function=GLOBAL_QUOTE&symbol=" + ticker + "&apikey=" + apiKey;
        JsonNode root = fetchJson(url);
        JsonNode q = root.path("Global Quote");

        if (!q.has("05. price")) {
            throw new ResourceNotFoundException("No quote data found for ticker: " + ticker);
        }

        return new StockQuote(
                text(q, "01. symbol"),
                num(q, "05. price"),
                num(q, "09. change"),
                pct(q, "10. change percent"),
                num(q, "02. open"),
                num(q, "03. high"),
                num(q, "04. low"),
                num(q, "08. previous close"),
                (long) num(q, "06. volume"),
                text(q, "07. latest trading day")
        );
    }

    // ------------------------------------------------------------------
    // OVERVIEW — fundamentals
    // ------------------------------------------------------------------
    @Cacheable(value = "stockOverview", key = "#ticker")
    public StockOverview getOverview(String ticker) {
        String url = BASE_URL + "?function=OVERVIEW&symbol=" + ticker + "&apikey=" + apiKey;
        JsonNode root = fetchJson(url);

        if (!root.has("Symbol") || root.path("Symbol").asText().isBlank()) {
            // Alpha Vantage returns {} for tickers without fundamentals (ETFs, some intl symbols)
            return new StockOverview(ticker, null, null, null, null, null, null, null, null, null, null, null);
        }

        return new StockOverview(
                text(root, "Symbol"),
                text(root, "Name"),
                text(root, "Sector"),
                text(root, "Industry"),
                text(root, "Description"),
                numOrNull(root, "MarketCapitalization"),
                numOrNull(root, "PERatio"),
                numOrNull(root, "EPS"),
                numOrNull(root, "DividendYield"),
                numOrNull(root, "Beta"),
                numOrNull(root, "52WeekHigh"),
                numOrNull(root, "52WeekLow")
        );
    }

    // ------------------------------------------------------------------
    // TIME_SERIES_DAILY — recent closes for charting + pattern signals
    // ------------------------------------------------------------------
    @Cacheable(value = "stockHistory", key = "#ticker")
    public List<Double> getRecentCloses(String ticker) {
        String url = BASE_URL + "?function=TIME_SERIES_DAILY&symbol=" + ticker
                + "&outputsize=compact&apikey=" + apiKey;
        JsonNode root = fetchJson(url);
        JsonNode series = root.path("Time Series (Daily)");

        List<Double> closes = new ArrayList<>();
        if (series.isObject()) {
            var it = series.fieldNames();
            int count = 0;
            while (it.hasNext() && count < 60) {
                String date = it.next();
                closes.add(num(series.path(date), "4. close"));
                count++;
            }
        }
        return closes; // most recent first
    }

    // ------------------------------------------------------------------
    // INTELLIGENCE — combines everything + Feature 4 pattern signals
    // ------------------------------------------------------------------
    public StockIntelligence getIntelligence(String ticker, SentimentService sentimentService) {
        StockQuote quote = getQuote(ticker);
        sleepBetweenCalls();
        StockOverview overview = getOverview(ticker);
        sleepBetweenCalls();
        List<Double> closes = getRecentCloses(ticker);

        List<String> signals = new ArrayList<>();
        String trend = "SIDEWAYS";
        String riskLevel = "LOW";

        // --- Trend: compare most recent close vs. 10 trading days ago ---
        if (closes.size() >= 11) {
            double recent = closes.get(0);
            double tenDaysAgo = closes.get(10);
            double moveePct = ((recent - tenDaysAgo) / tenDaysAgo) * 100;
            if (moveePct > 3) { trend = "UPWARD"; signals.add("Momentum Positive"); }
            else if (moveePct < -3) { trend = "DOWNWARD"; signals.add("Momentum Negative"); }
        }

        // --- Volatility: stdev of daily returns over recent window ---
        if (closes.size() >= 15) {
            List<Double> returns = new ArrayList<>();
            for (int i = 0; i < closes.size() - 1; i++) {
                returns.add((closes.get(i) - closes.get(i + 1)) / closes.get(i + 1));
            }
            double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = returns.stream().mapToDouble(r -> Math.pow(r - mean, 2)).average().orElse(0);
            double stdDev = Math.sqrt(variance) * 100;
            if (stdDev > 3.5) {
                signals.add("High Volatility");
                riskLevel = "HIGH";
            }
        }

        // --- 52-week high/low proximity ---
        if (overview.week52High() != null && overview.week52Low() != null && overview.week52High() > 0) {
            double pctFromHigh = ((overview.week52High() - quote.price()) / overview.week52High()) * 100;
            double pctFromLow = ((quote.price() - overview.week52Low()) / overview.week52Low()) * 100;
            if (pctFromHigh < 3) signals.add("Near 52-Week High");
            if (pctFromLow < 3) signals.add("Near 52-Week Low");
        }

        // --- Beta vs market ---
        if (overview.beta() != null && overview.beta() > 1.3) {
            signals.add("Beta Above Market");
            if (!riskLevel.equals("HIGH")) riskLevel = "MODERATE";
        }

        // --- Unusual single-day move ---
        if (Math.abs(quote.changePercent()) > 5) {
            signals.add("Unusual Volume");
            riskLevel = "HIGH";
        }

        // --- Sentiment, reusing your existing SentimentService ---
        String sentimentLabel = null;
        Double sentimentScore = null;
        try {
            var sentiment = sentimentService.getSentiment(ticker);
            sentimentLabel = (String) sentiment.getOrDefault("label", null);
            Object scoreObj = sentiment.get("score");
            if (scoreObj instanceof Number n) sentimentScore = n.doubleValue();
            if ("NEGATIVE".equals(sentimentLabel)) {
                signals.add("Negative News Pressure");
                if (riskLevel.equals("LOW")) riskLevel = "MODERATE";
            }
        } catch (Exception ignored) {
            // Sentiment service unavailable — signals/risk level proceed without it.
        }

        if (signals.isEmpty()) {
            signals.add("No unusual signals detected");
        }

        return new StockIntelligence(
                ticker, quote, overview, signals, riskLevel, trend,
                sentimentLabel, sentimentScore,
                "PortfolioGuard provides analytical insights, not financial advice."
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    // Alpha Vantage free tier enforces 1 request/second. getIntelligence()
    // makes 3 sequential calls (quote, overview, history) - without this,
    // the 2nd/3rd call can land in the same second and get throttled even
    // though @Cacheable means most repeat lookups never hit this path again.
    private void sleepBetweenCalls() {
        try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
    }

    private JsonNode fetchJson(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            String body = response.getBody();
            System.out.println("DEBUG fetchJson URL: " + url);
            System.out.println("DEBUG fetchJson status: " + response.getStatusCode());
            System.out.println("DEBUG fetchJson raw body: " + body);

            JsonNode root = mapper.readTree(body);
            if (root.has("Note") || root.has("Information")) {
                System.out.println("DEBUG rate limit field found: " + root.path("Note").asText() + " / " + root.path("Information").asText());
                throw new RuntimeException("Alpha Vantage rate limit reached. Please try again shortly.");
            }
            return root;
        } catch (Exception e) {
            System.out.println("DEBUG fetchJson EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch market data: " + e.getMessage());
        }
    }

    private String text(JsonNode node, String field) {
        return node.has(field) ? node.path(field).asText() : null;
    }

    private double num(JsonNode node, String field) {
        if (!node.has(field)) return 0;
        String raw = node.path(field).asText().replace("%", "");
        try { return Double.parseDouble(raw); } catch (NumberFormatException e) { return 0; }
    }

    private Double numOrNull(JsonNode node, String field) {
        if (!node.has(field)) return null;
        String raw = node.path(field).asText();
        if (raw.isBlank() || raw.equals("None") || raw.equals("-")) return null;
        try { return Double.parseDouble(raw); } catch (NumberFormatException e) { return null; }
    }

    private double pct(JsonNode node, String field) {
        return num(node, field);
    }
}
