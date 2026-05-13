package com.hotel.gateway.filter;

import com.hotel.gateway.config.GatewayApiProperties;
import com.hotel.gateway.handler.GatewayErrorResponder;
import com.hotel.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;
    private GatewayApiProperties properties;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        properties = new GatewayApiProperties();
        properties.setPublicPaths(List.of("/api/v1/auth/**", "/api/v1/hotels/**"));
        properties.setInternalServicePaths(List.of("GET:/api/v1/users/"));
        properties.setInternalNetworkRanges(List.of("172.20.0.0/16", "127.0.0.1/32"));
        filter = new JwtAuthenticationFilter(jwtUtil, new GatewayErrorResponder(), properties);
    }

    @Test
    void filter_SkipsAuthenticationForPublicPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, calledChain(chainCalled)).block(Duration.ofSeconds(1));

        assertTrue(chainCalled.get());
        verifyNoInteractions(jwtUtil);
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_AddsUserHeadersForValidToken() {
        when(jwtUtil.validateToken("valid-token")).thenReturn(true);
        when(jwtUtil.extractUserId("valid-token")).thenReturn("user-123");
        when(jwtUtil.extractUsername("valid-token")).thenReturn("alice@example.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn("ADMIN");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/bookings")
                        .header("Authorization", "Bearer valid-token"));
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();

        filter.filter(exchange, captured -> {
            capturedExchange.set(captured);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertEquals("user-123", capturedExchange.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("alice@example.com", capturedExchange.get().getRequest().getHeaders().getFirst("X-Username"));
        assertEquals("ADMIN", capturedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("true", capturedExchange.get().getRequest().getHeaders().getFirst("X-Authenticated"));
    }

    @Test
    void filter_ReturnsUnauthorizedWhenTokenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/bookings"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, calledChain(chainCalled)).block(Duration.ofSeconds(1));

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1))
                .contains("Missing authentication token"));
    }

    @Test
    void filter_BlocksExternalInternalServicePath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/123")
                        .header("X-Forwarded-For", "203.0.113.10"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, calledChain(chainCalled)).block(Duration.ofSeconds(1));

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    private GatewayFilterChain calledChain(AtomicBoolean chainCalled) {
        return exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };
    }
}
