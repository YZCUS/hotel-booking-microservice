package com.hotel.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Generate unique request ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        
        // Log request
        long startTime = System.currentTimeMillis();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String userId = request.getHeaders().getFirst("X-User-Id");
        
        log.info("Incoming request [{}]: {} {} from {} | User: {} | UA: {}", 
            requestId,
            request.getMethod(),
            request.getPath().value(),
            clientIp,
            userId != null ? userId : "anonymous",
            userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "unknown"
        );
        
        // Add request ID to response headers
        exchange.getResponse().getHeaders().add("X-Request-ID", requestId);
        
        return chain.filter(exchange)
            .doOnSuccess(aVoid -> {
                ServerHttpResponse response = exchange.getResponse();
                long duration = System.currentTimeMillis() - startTime;
                
                log.info("Response [{}]: {} {} -> {} in {}ms", 
                    requestId,
                    request.getMethod(),
                    request.getPath().value(),
                    response.getStatusCode(),
                    duration
                );
            })
            .doOnError(throwable -> {
                long duration = System.currentTimeMillis() - startTime;
                
                log.error("Error [{}]: {} {} failed after {}ms - {}", 
                    requestId,
                    request.getMethod(),
                    request.getPath().value(),
                    duration,
                    throwable.getMessage()
                );
            });
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
    
    @Override
    public int getOrder() {
        return -200; // Execute early to capture all requests
    }
}