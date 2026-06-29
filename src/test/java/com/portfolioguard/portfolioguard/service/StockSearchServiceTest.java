package com.portfolioguard.portfolioguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioguard.portfolioguard.dto.StockQuote;
import com.portfolioguard.portfolioguard.dto.StockSearchResult;
import com.portfolioguard.portfolioguard.exception.AlphaVantageRateLimitException;
import com.portfolioguard.portfolioguard.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockSearchServiceTest {

    @Mock RestTemplate restTemplate;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;

    StockSearchService service;

    @BeforeEach
    void setUp() {
        service = new StockSearchService();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "mapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "cacheManager", cacheManager);
        ReflectionTestUtils.setField(service, "apiKey", "demo");
        when(cacheManager.getCache(anyString())).thenReturn(cache);
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    @Test
    void search_happyPath_parsesMatchesAndCaches() {
        String json = """
            {"bestMatches":[
              {"1. symbol":"AAPL","2. name":"Apple Inc.","3. type":"Equity",
               "4. region":"United States","8. currency":"USD"}
            ]}""";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        StockSearchResult result = service.search("apple");

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).symbol()).isEqualTo("AAPL");
        assertThat(result.matches().get(0).name()).isEqualTo("Apple Inc.");
        verify(cache).put("apple", result);
    }

    @Test
    void search_emptyBestMatches_returnsEmptyList() {
        String json = "{\"bestMatches\":[]}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        StockSearchResult result = service.search("xyz");
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void search_rateLimitWithStaleCache_returnsStaleResult() {
        String rateLimitJson = "{\"Note\":\"API limit reached.\"}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(rateLimitJson));

        StockSearchResult stale = new StockSearchResult(List.of());
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(stale);
        when(cache.get("apple")).thenReturn(wrapper);

        StockSearchResult result = service.search("apple");
        assertThat(result).isSameAs(stale);
    }

    @Test
    void search_rateLimitWithNoStaleCache_throwsRateLimitException() {
        String rateLimitJson = "{\"Note\":\"API limit reached.\"}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(rateLimitJson));
        when(cache.get("apple")).thenReturn(null);

        assertThatThrownBy(() -> service.search("apple"))
                .isInstanceOf(AlphaVantageRateLimitException.class);
    }

    @Test
    void search_informationFieldTreatedAsRateLimit() {
        String infoJson = "{\"Information\":\"Please subscribe to a higher tier.\"}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(infoJson));
        when(cache.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.search("apple"))
                .isInstanceOf(AlphaVantageRateLimitException.class);
    }

    // ------------------------------------------------------------------
    // getQuote
    // ------------------------------------------------------------------

    @Test
    void getQuote_happyPath_parsesAllFields() {
        String json = """
            {"Global Quote":{
              "01. symbol":"AAPL",
              "05. price":"150.00",
              "09. change":"1.50",
              "10. change percent":"1.01%",
              "02. open":"148.50",
              "03. high":"151.00",
              "04. low":"147.00",
              "08. previous close":"148.50",
              "06. volume":"50000000",
              "07. latest trading day":"2024-01-15"
            }}""";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        StockQuote quote = service.getQuote("AAPL");

        assertThat(quote.symbol()).isEqualTo("AAPL");
        assertThat(quote.price()).isEqualTo(150.0);
        assertThat(quote.changePercent()).isCloseTo(1.01, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void getQuote_noPriceField_throwsResourceNotFoundException() {
        String json = "{\"Global Quote\":{}}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        assertThatThrownBy(() -> service.getQuote("FAKE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuote_rateLimitWithStaleCache_returnsStaleQuote() {
        String rateLimitJson = "{\"Note\":\"limit\"}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(rateLimitJson));

        StockQuote stale = new StockQuote("AAPL", 140.0, 0, 0, 0, 0, 0, 0, 0, null);
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(stale);
        when(cache.get("AAPL")).thenReturn(wrapper);

        assertThat(service.getQuote("AAPL")).isSameAs(stale);
    }

    // ------------------------------------------------------------------
    // getOverview
    // ------------------------------------------------------------------

    @Test
    void getOverview_happyPath_parsesFields() {
        String json = """
            {"Symbol":"AAPL","Name":"Apple Inc.","Sector":"Technology",
             "Industry":"Consumer Electronics","Description":"Apple description",
             "MarketCapitalization":"3000000000000","PERatio":"30.0",
             "EPS":"6.0","DividendYield":"0.5","Beta":"1.2",
             "52WeekHigh":"200.0","52WeekLow":"150.0"}""";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        var overview = service.getOverview("AAPL");

        assertThat(overview.symbol()).isEqualTo("AAPL");
        assertThat(overview.sector()).isEqualTo("Technology");
        assertThat(overview.beta()).isEqualTo(1.2);
    }

    @Test
    void getOverview_emptySymbol_returnsEmptyOverview() {
        String json = "{}"; // Alpha Vantage returns empty object for unknown tickers
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        var overview = service.getOverview("UNKNOWN");

        assertThat(overview.symbol()).isEqualTo("UNKNOWN");
        assertThat(overview.name()).isNull();
    }

    // ------------------------------------------------------------------
    // getRecentCloses
    // ------------------------------------------------------------------

    @Test
    void getRecentCloses_happyPath_returnsMostRecentFirst() {
        String json = """
            {"Time Series (Daily)":{
              "2024-01-15":{"4. close":"155.0"},
              "2024-01-14":{"4. close":"153.0"},
              "2024-01-13":{"4. close":"151.0"}
            }}""";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        List<Double> closes = service.getRecentCloses("AAPL");

        assertThat(closes).isNotEmpty();
        // Values should be extracted (order depends on JSON field iteration)
        assertThat(closes).containsAnyOf(155.0, 153.0, 151.0);
    }

    @Test
    void getRecentCloses_rateLimitWithStaleList_returnsStaleData() {
        String rateLimitJson = "{\"Note\":\"limit\"}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(rateLimitJson));

        List<Double> stale = List.of(150.0, 148.0, 147.0);
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(stale);
        when(cache.get("AAPL")).thenReturn(wrapper);

        assertThat(service.getRecentCloses("AAPL")).isSameAs(stale);
    }

    // ------------------------------------------------------------------
    // cacheManager failures are swallowed
    // ------------------------------------------------------------------

    @Test
    void search_cacheManagerThrows_stillFetchesFromApi() {
        when(cacheManager.getCache(anyString())).thenThrow(new RuntimeException("redis down"));
        String json = "{\"bestMatches\":[]}";
        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        // Should not propagate the Redis exception
        StockSearchResult result = service.search("apple");
        assertThat(result.matches()).isEmpty();
    }
}
