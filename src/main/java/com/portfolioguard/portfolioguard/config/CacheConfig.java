package com.portfolioguard.portfolioguard.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                // "pg:v1:" prefix means any stale entries written under the old serializer
                // format are never read — they simply become cache misses. Bump to v2
                // whenever the serializer configuration changes again.
                .computePrefixWith(cacheName -> "pg:v1:" + cacheName + ":")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    // Makes ALL cache errors (deserialization failures, connection errors, timeouts)
    // non-fatal. On a cache-get error the operation falls through to calling the
    // real method; on a cache-put error the result is still returned to the caller.
    // This ensures Redis can never crash a live request.
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }
}
