package com.hotel.gateway.filter;

import com.hotel.gateway.handler.GatewayErrorResponder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class RateLimitFilterRedisIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RateLimitFilter filter;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext(stringSerializer)
                .key(stringSerializer)
                .value(stringSerializer)
                .hashKey(stringSerializer)
                .hashValue(stringSerializer)
                .build();

        ReactiveRedisTemplate<String, String> redisTemplate =
                new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
        filter = new RateLimitFilter(redisTemplate, new GatewayErrorResponder());
    }

    @AfterAll
    static void tearDownRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void filter_StoresRateLimitCounterInRedisAcrossRequests() {
        AtomicInteger chainCalls = new AtomicInteger();
        GatewayFilterChain chain = exchange -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        };

        MockServerWebExchange firstExchange = exchangeFor("203.0.113.25");
        filter.filter(firstExchange, chain).block(Duration.ofSeconds(3));

        MockServerWebExchange secondExchange = exchangeFor("203.0.113.25");
        filter.filter(secondExchange, chain).block(Duration.ofSeconds(3));

        assertEquals(2, chainCalls.get());
        assertEquals("59", firstExchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        assertEquals("58", secondExchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    private MockServerWebExchange exchangeFor(String ipAddress) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/hotels")
                .header("X-Real-IP", ipAddress));
    }
}
