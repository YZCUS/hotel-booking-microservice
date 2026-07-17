package com.hotel.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.jwt.secret=gateway-test-secret-key-with-more-than-64-characters-1234567890abcdef",
        "services.user-service.url=http://127.0.0.1:1",
        "spring.data.redis.password=test",
        "app.internal.service-secret=test-internal-secret"
})
class GatewaySecurityIntegrationTest {

    private static final String SECRET =
            "gateway-test-secret-key-with-more-than-64-characters-1234567890abcdef";

    @LocalServerPort
    private int port;

    @MockBean
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private RouteLocator routeLocator;

    private WebTestClient webTestClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReactiveValueOperations<String, String> operations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.increment(any(String.class))).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(any(String.class), any(Duration.class))).thenReturn(Mono.just(true));

        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    void validJwtReachesGatewayFilterWithoutDefaultBasicUnauthorizedResponse() {
        webTestClient.get()
                .uri("/api/v1/users/profile")
                .header("Authorization", "Bearer " + token("ADMIN"))
                .exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }

    @Test
    void protectedRouteWithoutJwtIsUnauthorized() {
        webTestClient.get()
                .uri("/api/v1/users/profile")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void actuatorOnlyExposesHealth() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.post()
                .uri("/actuator/gateway/refresh")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri("/actuator/gateway/routes/should-not-exist")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void favoritesRouteIsRegistered() {
        List<String> routeIds = routeLocator.getRoutes()
                .map(route -> route.getId())
                .collectList()
                .block(Duration.ofSeconds(5));

        assertTrue(routeIds != null && routeIds.contains("hotel-favorites-service"));
        assertTrue(routeIds != null && routeIds.contains("notification-service-health"));
    }

    @Test
    void corsAllowsConfiguredOriginAndRejectsArbitraryCredentialedOrigin() {
        webTestClient.options()
                .uri("/actuator/health")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000");

        webTestClient.options()
                .uri("/actuator/health")
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    private String token(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("admin@example.com")
                .claim("userId", UUID.randomUUID().toString())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }
}
