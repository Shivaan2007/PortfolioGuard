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

    @Cacheable(value = "sentiment", key = "#ticker")
    public Map<String, Object> getSentiment(String ticker) {
        try {
            String url = sentimentServiceUrl + "/sentiment/" + ticker;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            return Map.of(
                    "ticker", ticker,
                    "score", 0.0,
                    "label", "NEUTRAL",
                    "error", "Sentiment service unavailable"
            );
        }
    }
}