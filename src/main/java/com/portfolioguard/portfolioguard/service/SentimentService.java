package com.portfolioguard.portfolioguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class SentimentService {

    @Value("${sentiment.service.url:http://localhost:5002}")
    private String sentimentServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // unless condition prevents the error-fallback map from being cached —
    // the sentiment service might recover, and we don't want to lock in
    // a "unavailable" response for 15 minutes.
    @Cacheable(value = "sentiment", key = "#ticker", unless = "#result.containsKey('error')")
    public Map<String, Object> getSentiment(String ticker) {
        try {
            String url = sentimentServiceUrl + "/sentiment/" + ticker;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            // Return a mutable HashMap so Jackson can serialize/deserialize it
            // if this response ever does reach the cache (it shouldn't, due to
            // the unless condition above, but defensive typing avoids the
            // ImmutableCollections deserialization bug).
            Map<String, Object> fallback = new java.util.HashMap<>();
            fallback.put("ticker", ticker);
            fallback.put("score", 0.0);
            fallback.put("label", "NEUTRAL");
            fallback.put("error", "Sentiment service unavailable");
            return fallback;
        }
    }
}