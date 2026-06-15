package com.portfolioguard.portfolioguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Service
public class MarketDataService {

    @Value("${price.service.url:http://localhost:5003}")
    private String priceServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public double getCurrentPrice(String ticker) {
        try {
            String url = priceServiceUrl + "/price/" + ticker;
            String json = restTemplate.getForObject(url, String.class);
            Map<String, Object> response = mapper.readValue(json, Map.class);
            Object price = response.get("price");
            return ((Number) price).doubleValue();
        } catch (Exception e) {
            System.out.println("Failed to fetch price for " + ticker + ": " + e.getMessage());
            throw new RuntimeException("Failed to fetch price for: " + ticker);
        }
    }

    public List<Double> getDailyReturns(String ticker, int days) {
        try {
            String url = priceServiceUrl + "/returns/" + ticker + "/" + days;
            String json = restTemplate.getForObject(url, String.class);
            Map<String, Object> response = mapper.readValue(json, Map.class);
            List<Number> returns = (List<Number>) response.get("returns");
            List<Double> result = new ArrayList<>();
            for (Number r : returns) result.add(r.doubleValue());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("failed to fetch returns" + ticker);
        }
    }

    public String fetchStockData(String ticker) {
        try {
            String url = priceServiceUrl + "/price/" + ticker;
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public String fetchRawDailyData(String ticker) {
        return fetchStockData(ticker);
    }
}
