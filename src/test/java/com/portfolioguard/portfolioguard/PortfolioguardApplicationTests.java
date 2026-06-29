package com.portfolioguard.portfolioguard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "price-updates")
class PortfolioguardApplicationTests {

    // Mock Redis so the context loads without a running Redis instance
    @MockBean RedisConnectionFactory redisConnectionFactory;
    @MockBean ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @Test
    void contextLoads() {
    }
}
