package com.portfolioguard.portfolioguard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SentimentServiceTest {

    @Mock RestTemplate restTemplate;

    SentimentService sentimentService;

    @BeforeEach
    void setUp() {
        sentimentService = new SentimentService();
        ReflectionTestUtils.setField(sentimentService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(sentimentService, "sentimentServiceUrl", "http://localhost:5002");
    }

    @Test
    void getSentiment_success_returnsServiceResponse() {
        Map<String, Object> response = Map.of("ticker", "AAPL", "score", 0.8, "label", "POSITIVE");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        Map<String, Object> result = sentimentService.getSentiment("AAPL");

        assertThat(result.get("label")).isEqualTo("POSITIVE");
        assertThat(result.get("score")).isEqualTo(0.8);
    }

    @Test
    void getSentiment_serviceUnavailable_returnsFallbackWithNeutralLabel() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> result = sentimentService.getSentiment("AAPL");

        assertThat(result.get("label")).isEqualTo("NEUTRAL");
        assertThat(result.get("score")).isEqualTo(0.0);
        assertThat(result.get("ticker")).isEqualTo("AAPL");
        assertThat(result).containsKey("error");
    }

    @Test
    void getSentiment_serviceUnavailable_fallbackIsMutableMap() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("down"));

        Map<String, Object> result = sentimentService.getSentiment("TSLA");

        // Must be mutable — ImmutableCollections caused Redis deserialization crashes
        result.put("extra", "value");
        assertThat(result).containsKey("extra");
    }

    @Test
    void getSentiment_callsCorrectUrl() {
        Map<String, Object> response = Map.of("ticker", "TSLA", "score", 0.3, "label", "NEGATIVE");
        when(restTemplate.getForObject("http://localhost:5002/sentiment/TSLA", Map.class)).thenReturn(response);

        Map<String, Object> result = sentimentService.getSentiment("TSLA");
        assertThat(result.get("label")).isEqualTo("NEGATIVE");
    }
}
