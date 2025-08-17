package com.hotel.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    private static final int REQUESTS_PER_MINUTE = 60;
    private static final int AUTHENTICATED_REQUESTS_PER_MINUTE = 120;
    private static final List<String> EXCLUDED_PATHS = List.of(
        "/actuator/",
        "/health/",
        "/fallback/"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // Skip rate limiting for excluded paths
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }
        
        String clientId = getClientIdentifier(request);
        boolean isAuthenticated = isAuthenticatedRequest(request);
        int rateLimit = isAuthenticated ? AUTHENTICATED_REQUESTS_PER_MINUTE : REQUESTS_PER_MINUTE;
        
        String key = "rate_limit:" + clientId;
        
        return redisTemplate.opsForValue()
            .increment(key)
            .flatMap(count -> {
                if (count == 1) {
                    // First request, set expiration
                    return redisTemplate.expire(key, Duration.ofMinutes(1))
                        .then(Mono.just(count));
                }
                return Mono.just(count);
            })
            .flatMap(count -> {
                if (count > rateLimit) {
                    log.warn("Rate limit exceeded for client: {} (requests: {}, limit: {})", 
                        clientId, count, rateLimit);
                    return onError(exchange, "Rate limit exceeded. Please try again later.", 
                        HttpStatus.TOO_MANY_REQUESTS);
                }
                
                // Add rate limit headers
                exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(rateLimit));
                exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", 
                    String.valueOf(Math.max(0, rateLimit - count)));
                
                log.debug("Rate limit check passed for client: {} ({}/{})", clientId, count, rateLimit);
                return chain.filter(exchange);
            })
            .onErrorResume(e -> {
                log.error("Rate limiting error for client: {}", clientId, e);
                // If Redis is down, allow the request to proceed
                return chain.filter(exchange);
            });
    }
    
    private String getClientIdentifier(ServerHttpRequest request) {
        // Priority: User ID (if authenticated) > X-Forwarded-For > X-Real-IP > Remote Address
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }
        
        String clientIp = getClientIp(request);
        return "ip:" + clientIp;
    }
    
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
    
    private boolean isAuthenticatedRequest(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }
    
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream()
            .anyMatch(excluded -> path.startsWith(excluded));
    }
    
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        
        String errorResponse = String.format(
            "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
            httpStatus.getReasonPhrase(),
            message,
            java.time.Instant.now().toString()
        );
        
        byte[] bytes = errorResponse.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("Retry-After", "60"); // Suggest retry after 60 seconds
        
        return response.writeWith(Mono.just(buffer));
    }
    
    @Override
    public int getOrder() {
        return -99; // Execute after JWT filter but before other filters
    }
}