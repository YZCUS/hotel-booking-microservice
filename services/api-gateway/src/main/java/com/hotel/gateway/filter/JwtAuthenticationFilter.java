package com.hotel.gateway.filter;

import com.hotel.gateway.config.GatewayApiProperties;
import com.hotel.gateway.handler.GatewayErrorResponder;
import com.hotel.gateway.util.IpAddressUtil;
import com.hotel.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    private final JwtUtil jwtUtil;
    private final GatewayErrorResponder errorResponder;
    private final GatewayApiProperties gatewayApiProperties;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();
        
        log.debug("Processing request: {} {}", method, path);

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            log.debug("Skipping JWT authentication for public path: {}", path);
            return chain.filter(exchange);
        }
        
        // Check for internal service communication
        if (isInternalServicePath(method, path)) {
            String clientIp = IpAddressUtil.getClientIpAddress(request);
            if (!isInternalNetwork(clientIp)) {
                log.warn("Blocking external access to internal service path: {} {} from IP: {}", method, path, clientIp);
                return errorResponder.createErrorResponse(exchange, HttpStatus.FORBIDDEN, "Access denied: Internal service path");
            }
            log.debug("Allowing internal service access to: {} {} from IP: {}", method, path, clientIp);
            return chain.filter(exchange);
        }
        
        // Extract and validate JWT token
        String token = extractToken(request);
        
        if (token == null) {
            log.warn("Missing authentication token for: {} {}", method, path);
            return errorResponder.createErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing authentication token");
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
                return errorResponder.createErrorResponse(exchange, HttpStatus.UNAUTHORIZED,"Missing authentication token");
            }
        } catch (Exception e) {
            log.error("JWT validation error for: {} {}", method, path, e);
            return errorResponder.createErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Token validation failed");
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

    private boolean isPublicPath(String path) {
        return gatewayApiProperties.getPublicPaths().stream()
                .map(p -> p.replace("/**", ""))
                .anyMatch(path::startsWith);
    }
    
    private boolean isInternalServicePath(String method, String path) {
        String methodPath = method + ":" + path;
        return gatewayApiProperties.getInternalServicePaths().stream()
                .anyMatch(methodPath::startsWith);
    }

    private boolean isInternalNetwork(String ip) {
        if (ip == null || "unknown".equals(ip) || ip.equals("localhost")) {
            return "localhost".equals(ip); // localhost is safe
        }

        // check ip with docker internal network ranges
        for (String cidr : gatewayApiProperties.getInternalNetworkRanges()) {
            if (isIpInRange(ip, cidr)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpInRange(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String cidrIp = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            long ipAddress = ipToLong(java.net.InetAddress.getByName(ip));
            long cidrIpAddress = ipToLong(java.net.InetAddress.getByName(cidrIp));
            long mask = (-1L) << (32 - prefix);

            return (ipAddress & mask) == (cidrIpAddress & mask);
        } catch (Exception e) {
            log.warn("Failed to check if IP {} is in CIDR range {}: {}", ip, cidr, e.getMessage());
            return false;
        }
    }

    private long ipToLong(java.net.InetAddress ip) {
        byte[] octets = ip.getAddress();
        long result = 0;
        for (byte octet : octets) {
            result <<= 8;
            result |= octet & 0xff;
        }
        return result;
    }
    
    @Override
    public int getOrder() {
        return -100; // Execute before other filters
    }
}