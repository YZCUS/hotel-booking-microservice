package com.hotel.gateway.filter;

import com.hotel.gateway.handler.GatewayErrorResponder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private ReactiveRedisTemplate<String, String> redisTemplate;
    private RateLimitFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(ReactiveRedisTemplate.class);
        filter = new RateLimitFilter(redisTemplate, new GatewayErrorResponder());
    }

    @Test
    void filter_SkipsExcludedPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, calledChain(chainCalled)).block(Duration.ofSeconds(1));

        assertTrue(chainCalled.get());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void filter_AllowsRequestAndAddsRateLimitHeaders() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rate_limit:ip:198.51.100.10")),
                eq(List.of("60")))).thenReturn(Flux.just(1L));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hotels")
                        .header("X-Real-IP", "198.51.100.10"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, calledChain(chainCalled)).block(Duration.ofSeconds(1));

        assertTrue(chainCalled.get());
        assertEquals("60", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
        assertEquals("59", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    @Test
    void filter_ReturnsTooManyRequestsAfterLimitExceeded() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rate_limit:ip:198.51.100.10")),
                eq(List.of("60")))).thenReturn(Flux.just(61L));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/hotels")
                        .header("X-Real-IP", "198.51.100.10"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, calledChain(chainCalled)).block(Duration.ofSeconds(1));

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("60", exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void filter_UsesUserIdAndAuthenticatedLimitWhenPresent() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("rate_limit:user:user-123")),
                eq(List.of("60")))).thenReturn(Flux.just(2L));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bookings")
                        .header("X-User-Id", "user-123")
                        .header("Authorization", "Bearer valid-token"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, calledChain(chainCalled)).block(Duration.ofSeconds(1));

        assertTrue(chainCalled.get());
        assertEquals("120", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
        assertEquals("118", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    private GatewayFilterChain calledChain(AtomicBoolean chainCalled) {
        return exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };
    }
}
