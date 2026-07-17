package com.hotel.gateway.filter;

import com.hotel.gateway.handler.GatewayErrorResponder;
import com.hotel.gateway.util.IpAddressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final GatewayErrorResponder errorResponder;
    
    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate, GatewayErrorResponder errorResponder) {
        this.redisTemplate = redisTemplate;
        this.errorResponder =  errorResponder;
    }
    
    private static final int REQUESTS_PER_MINUTE = 60;
    private static final int AUTHENTICATED_REQUESTS_PER_MINUTE = 120;
    private static final String RATE_LIMIT_WINDOW_SECONDS = "60";
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
        "local current = redis.call('incr', KEYS[1]); " +
            "if current == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; " +
            "return current",
        Long.class);
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
        
        return redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), List.of(RATE_LIMIT_WINDOW_SECONDS))
            .next()
            .switchIfEmpty(Mono.error(new IllegalStateException("Redis rate limit script returned no result")))
            .flatMap(count -> {
                if (count > rateLimit) {
                    log.warn("Rate limit exceeded for client: {} (requests: {}, limit: {})", 
                        clientId, count, rateLimit);
                    Map<String, String> headers = Map.of("Retry-After", "60"); // Suggest retry after 60 seconds
                    return errorResponder.createErrorResponse(exchange,
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again later.", headers);
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
        
        String clientIp = IpAddressUtil.getClientIpAddress(request);
        return "ip:" + clientIp;
    }
    
    private boolean isAuthenticatedRequest(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }
    
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream()
            .anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -99; // Execute after JWT filter but before other filters
    }
}
