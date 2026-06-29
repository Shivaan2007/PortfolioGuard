package com.portfolioguard.portfolioguard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class CacheConfig {

    /**
     * Spring's default Redis cache value serializer is Java's native
     * serialization, which doesn't play well with Java records (StockQuote,
     * StockOverview, etc.) used by StockSearchService. This bean replaces
     * the auto-configured RedisCacheManager with one using a Jackson-based
     * JSON serializer instead.
     *
     * IMPORTANT: GenericJackson2JsonRedisSerializer's no-arg constructor
     * builds its OWN internal ObjectMapper with type-info embedding handled
     * safely and consistently for cache round-trips. Do NOT pass in a
     * manually-configured ObjectMapper with activateDefaultTyping() - doing
     * so caused a real bug where cached values became unreadable across
     * different call sites (WRAPPER_ARRAY deserialization errors).
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
