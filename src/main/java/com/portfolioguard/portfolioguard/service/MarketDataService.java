package com.portfolioguard.portfolioguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.cache.annotation.Cacheable;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;



@Service
public class MarketDataService {
    @Value("${alphavantage.api.key}")
    private String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String BASE_URL = "https://www.alphavantage.co/query";

    @Cacheable(value = "stockData", key = "#ticker")
    public String fetchStockData(String ticker) {


        String url =
                BASE_URL
                        + "?function=GLOBAL_QUOTE"
                        + "&symbol=" + ticker
                        + "&apikey=" + apiKey;

        return restTemplate.getForObject(
                url,
                String.class
        );
    }

    public double getCurrentPrice(String ticker) {
        try {
            String json = fetchStockData(ticker);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> response = mapper.readValue(json, Map.class);
            Map<String, Object> globalQuote = (Map<String, Object>) response.get("Global Quote");
            String price = (String) globalQuote.get("05. price");
            return Double.parseDouble(price);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch price for: " + ticker);
        }
    }

    public List<Double> getDailyReturns(String ticker, int days) {
        try {
            String url = BASE_URL + "?function=TIME_SERIES_DAILY&symbol=" + ticker + "&outputsize=compact&apikey=" + apiKey;
            String json = restTemplate.getForObject(url, String.class);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> response = mapper.readValue(json, Map.class);
            Map<String, Object> timeSeries = (Map<String, Object>) response.get("Time Series (Daily)");
            List<String> allDates = new ArrayList<>(timeSeries.keySet());
            List<String> dates = allDates.subList(0, Math.min(days + 1, allDates.size()));
            List<Double> closingPrices = new ArrayList<>();
            for (String date : dates) {
                Map<String, Object> dayData = (Map<String, Object>) timeSeries.get(date);
                String closePrice = (String) dayData.get("4. close");
                closingPrices.add(Double.parseDouble(closePrice));
            }

            List<Double> dailyReturns = new ArrayList<>();
            for (int i = 1; i < closingPrices.size(); i++) {
                double returnVal = (closingPrices.get(i) - closingPrices.get(i - 1))
                        / closingPrices.get(i - 1);
                dailyReturns.add(returnVal);
            }
            return dailyReturns;
        } catch (Exception e) {
            throw new RuntimeException("failed to fetch returns" + ticker);
        }
    }


    public String fetchRawDailyData(String ticker) {
        String url = BASE_URL
                + "?function=TIME_SERIES_DAILY&symbol=" + ticker
                + "&outputsize=compact&apikey=" + apiKey;
        return restTemplate.getForObject(url, String.class);
    }

}

