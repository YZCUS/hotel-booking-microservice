package com.hotel.gateway.filter;

import com.hotel.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    private final JwtUtil jwtUtil;

    // Paths that should not require authentication
    private static final List<String> EXCLUDED_PATHS = List.of(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/hotels",
        "/api/v1/search",
        "/api/v1/inventory/check-availability",
        "/health/",
        "/actuator/",
        "/fallback/"
    );

    // Paths that allow public read access
    private static final List<String> PUBLIC_READ_PATHS = List.of(
        "GET:/api/v1/hotels",
        "GET:/api/v1/search"
    );
    
    // Internal service communication paths - only allow from internal network
    private static final List<String> INTERNAL_SERVICE_PATHS = List.of(
        "GET:/api/v1/users/"
    );
    
    // Docker internal network CIDR ranges
    private static final List<String> INTERNAL_NETWORK_RANGES = List.of(
        "172.20.0.0/16",  // Docker default bridge network
        "172.16.0.0/12",  // Docker custom networks
        "127.0.0.1/32",   // Localhost
        "10.0.0.0/8"      // Private network range
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();
        
        log.debug("Processing request: {} {}", method, path);
        
        // Skip authentication for excluded paths
        if (isExcludedPath(path)) {
            log.debug("Skipping authentication for excluded path: {}", path);
            return chain.filter(exchange);
        }
        
        // Allow public read access to certain endpoints
        if (isPublicReadPath(method, path)) {
            log.debug("Allowing public read access to: {} {}", method, path);
            return chain.filter(exchange);
        }
        
        // Check for internal service communication
        if (isInternalServicePath(method, path)) {
            String clientIp = getClientIpAddress(request);
            if (!isInternalNetwork(clientIp)) {
                log.warn("Blocking external access to internal service path: {} {} from IP: {}", method, path, clientIp);
                return onError(exchange, "Access denied: Internal service path", HttpStatus.FORBIDDEN);
            }
            log.debug("Allowing internal service access to: {} {} from IP: {}", method, path, clientIp);
            return chain.filter(exchange);
        }
        
        // Extract and validate JWT token
        String token = extractToken(request);
        
        if (token == null) {
            log.warn("Missing authentication token for: {} {}", method, path);
            return onError(exchange, "Missing authentication token", HttpStatus.UNAUTHORIZED);
        }
        
        try {
            if (jwtUtil.validateToken(token)) {
                String userId = jwtUtil.extractUserId(token);
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);
                
                log.debug("Authenticated user: {} (ID: {}, Role: {})", username, userId, role);
                
                // Add user information to headers for downstream services
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Username", username != null ? username : "")
                    .header("X-User-Role", role != null ? role : "USER")
                    .header("X-Authenticated", "true")
                    .build();
                
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } else {
                log.warn("Invalid JWT token for: {} {}", method, path);
                return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            log.error("JWT validation error for: {} {}", method, path, e);
            return onError(exchange, "Token validation failed", HttpStatus.UNAUTHORIZED);
        }
    }
    
    private String extractToken(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.getFirst();
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        return null;
    }
    
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream()
            .anyMatch(excluded -> path.startsWith(excluded));
    }
    
    private boolean isPublicReadPath(String method, String path) {
        String methodPath = method + ":" + path;
        return PUBLIC_READ_PATHS.stream()
            .anyMatch(publicPath -> {
                if (publicPath.contains("*")) {
                    String pattern = publicPath.replace("*", ".*");
                    return methodPath.matches(pattern);
                }
                return methodPath.startsWith(publicPath);
            });
    }
    
    private boolean isInternalServicePath(String method, String path) {
        String methodPath = method + ":" + path;
        return INTERNAL_SERVICE_PATHS.stream()
            .anyMatch(internalPath -> methodPath.startsWith(internalPath));
    }
    
    private String getClientIpAddress(ServerHttpRequest request) {
        // Check X-Forwarded-For header first (in case of proxy)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fall back to remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
    
    private boolean isInternalNetwork(String ip) {
        if (ip == null || "unknown".equals(ip)) {
            return false;
        }
        
        // Simple IP range checking for common internal networks
        return ip.startsWith("172.") || 
               ip.startsWith("10.") || 
               ip.startsWith("127.") || 
               ip.equals("localhost");
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
        return response.writeWith(Mono.just(buffer));
    }
    
    @Override
    public int getOrder() {
        return -100; // Execute before other filters
    }
}