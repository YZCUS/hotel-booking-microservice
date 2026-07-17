package com.hotel.gateway.filter;

import com.hotel.gateway.config.GatewayApiProperties;
import com.hotel.gateway.handler.GatewayErrorResponder;
import com.hotel.gateway.util.InternalServiceTokenProvider;
import com.hotel.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private AtomicReference<ServerWebExchange> forwardedExchange;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);

        GatewayApiProperties properties = new GatewayApiProperties();
        properties.setPublicPaths(List.of(
                "/api/v1/auth/**",
                "/api/v1/hotels/**",
                "/api/v1/search/**",
                "/api/v1/inventory/check-availability",
                "/health/**",
                "/actuator/health",
                "/fallback/**"
        ));

        filter = new JwtAuthenticationFilter(jwtUtil, new GatewayErrorResponder(), properties,
                new InternalServiceTokenProvider("test-shared-secret"));
        chain = mock(GatewayFilterChain.class);
        forwardedExchange = new AtomicReference<>();
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            forwardedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });
    }

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings/me"));

        filter.filter(exchange, chain).block();

        assertSame(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void publicPathPrefixCollisionWithoutTokenIsRejected() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/authentication/admin"));

        filter.filter(exchange, chain).block();

        assertSame(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void validTokenForProtectedRouteForwardsVerifiedIdentityAndRole() {
        String token = "valid-token";
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn("2a1d665b-1b43-4892-a5ae-b8a8779d8d3a");
        when(jwtUtil.extractEmail(token)).thenReturn("admin@example.com");
        when(jwtUtil.extractRole(token)).thenReturn("ADMIN");

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "USER"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwardedExchange.get().getRequest().getHeaders();
        assertEquals(List.of("2a1d665b-1b43-4892-a5ae-b8a8779d8d3a"), headers.get("X-User-Id"));
        assertEquals(List.of("admin@example.com"), headers.get("X-User-Email"));
        assertEquals(List.of("ADMIN"), headers.get("X-User-Role"));
        assertEquals(List.of("true"), headers.get("X-Authenticated"));
    }

    @Test
    void invalidTokenForProtectedRouteReturnsUnauthorized() {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"));

        filter.filter(exchange, chain).block();

        assertSame(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void hotelGetWithoutTokenIsAnonymousButSpoofedIdentityHeadersAreRemoved() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/hotels")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Email", "spoofed@example.com")
                .header("X-Username", "legacy-spoofed@example.com")
                .header("X-User-Role", "ADMIN")
                .header("X-Authenticated", "true")
                .header("X-Internal-Service", "attacker")
                .header("X-Internal-Token", "spoofed-secret")
                .header("X-Service", "notification-service")
                .header("X-Gateway", "attacker-gateway")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Real-IP", "203.0.113.8")
                .header("Forwarded", "for=203.0.113.9"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwardedExchange.get().getRequest().getHeaders();
        assertNull(headers.getFirst("X-User-Id"));
        assertNull(headers.getFirst("X-User-Email"));
        assertNull(headers.getFirst("X-Username"));
        assertNull(headers.getFirst("X-User-Role"));
        assertNull(headers.getFirst("X-Authenticated"));
        assertEquals("api-gateway", headers.getFirst("X-Internal-Service"));
        assertEquals(32, headers.getFirst("X-Internal-Token").length());
        org.junit.jupiter.api.Assertions.assertNotEquals("spoofed-secret",
                headers.getFirst("X-Internal-Token"));
        assertNull(headers.getFirst("X-Service"));
        assertNull(headers.getFirst("X-Gateway"));
        assertNull(headers.getFirst("X-Forwarded-For"));
        assertNull(headers.getFirst("X-Real-IP"));
        assertNull(headers.getFirst("Forwarded"));
    }

    @Test
    void internalHotelExportWithoutTokenIsRejectedAtGateway() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/hotels/export"));

        filter.filter(exchange, chain).block();

        assertSame(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void hotelGetWithValidTokenForwardsVerifiedIdentity() {
        String token = "valid-token";
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn("2a1d665b-1b43-4892-a5ae-b8a8779d8d3a");
        when(jwtUtil.extractEmail(token)).thenReturn("staff@example.com");
        when(jwtUtil.extractRole(token)).thenReturn("HOTEL_STAFF");

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        assertEquals("2a1d665b-1b43-4892-a5ae-b8a8779d8d3a",
                forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("HOTEL_STAFF",
                forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void hotelGetWithInvalidTokenReturnsUnauthorized() {
        when(jwtUtil.validateToken("expired-token")).thenReturn(false);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/hotels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"));

        filter.filter(exchange, chain).block();

        assertSame(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void hotelWriteWithoutTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/hotels")
                .header("X-User-Role", "ADMIN"));

        filter.filter(exchange, chain).block();

        assertSame(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void tokenWithoutRoleClaimIsRejectedInsteadOfDefaultingToUser() {
        String token = "legacy-token-without-role";
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractUserId(token)).thenReturn("2a1d665b-1b43-4892-a5ae-b8a8779d8d3a");
        when(jwtUtil.extractEmail(token)).thenReturn("legacy@example.com");
        when(jwtUtil.extractRole(token)).thenReturn(null);

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        assertSame(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> requestBuilder) {
        return MockServerWebExchange.from(requestBuilder.build());
    }
}
