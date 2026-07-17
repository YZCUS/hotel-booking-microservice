package com.hotel.gateway.filter;

import com.hotel.gateway.config.GatewayApiProperties;
import com.hotel.gateway.handler.GatewayErrorResponder;
import com.hotel.gateway.util.InternalServiceTokenProvider;
import com.hotel.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
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

    private static final String HOTELS_PATH = "/api/v1/hotels";
    private static final String HOTEL_EXPORT_PATH = HOTELS_PATH + "/export";
    private static final List<String> TRUSTED_HEADERS = List.of(
            "X-User-Id",
            "X-User-Email",
            "X-Username",
            "X-User-Role",
            "X-Authenticated",
            "X-Internal-Service",
            "X-Internal-Token",
            "X-Service",
            "X-Gateway",
            "X-Forwarded-For",
            "X-Real-IP",
            "Forwarded"
    );
    
    private final JwtUtil jwtUtil;
    private final GatewayErrorResponder errorResponder;
    private final GatewayApiProperties gatewayApiProperties;
    private final InternalServiceTokenProvider internalServiceTokenProvider;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = addTrustedInternalHeaders(removeUntrustedHeaders(exchange.getRequest()));
        String path = request.getPath().value();
        String method = request.getMethod().name();
        
        log.debug("Processing request: {} {}", method, path);

        ServerWebExchange sanitizedExchange = exchange.mutate().request(request).build();
        boolean publicRequest = isPublicRequest(request);
        String token = extractToken(request);

        if (token == null) {
            if (publicRequest) {
                log.debug("Allowing anonymous request to public endpoint: {} {}", method, path);
                return chain.filter(sanitizedExchange);
            }
            log.warn("Missing authentication token for: {} {}", method, path);
            return errorResponder.createErrorResponse(sanitizedExchange, HttpStatus.UNAUTHORIZED,
                    "Missing authentication token");
        }
        
        try {
            if (jwtUtil.validateToken(token)) {
                String userId = jwtUtil.extractUserId(token);
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);

                if (userId == null || userId.isBlank() || email == null || email.isBlank()
                        || role == null || role.isBlank()) {
                    log.warn("JWT is missing required identity claims for: {} {}", method, path);
                    return errorResponder.createErrorResponse(sanitizedExchange, HttpStatus.UNAUTHORIZED,
                            "Authentication token is missing required claims");
                }

                log.debug("Authenticated user: {} (ID: {}, Role: {})", email, userId, role);
                
                // Add user information to headers for downstream services
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email)
                    .header("X-User-Role", role)
                    .header("X-Authenticated", "true")
                    .build();
                
                return chain.filter(sanitizedExchange.mutate().request(modifiedRequest).build());
            } else {
                log.warn("Invalid JWT token for: {} {}", method, path);
                return errorResponder.createErrorResponse(sanitizedExchange, HttpStatus.UNAUTHORIZED,
                        "Invalid or expired authentication token");
            }
        } catch (Exception e) {
            log.error("JWT validation error for: {} {}", method, path, e);
            return errorResponder.createErrorResponse(sanitizedExchange, HttpStatus.UNAUTHORIZED,
                    "Token validation failed");
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

    private boolean isPublicRequest(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }

        String path = request.getPath().value();
        if (HOTEL_EXPORT_PATH.equals(path)) {
            return false;
        }
        if (path.equals(HOTELS_PATH) || path.startsWith(HOTELS_PATH + "/")) {
            return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
        }

        return gatewayApiProperties.getPublicPaths().stream()
                .anyMatch(pattern -> matchesPublicPath(pattern, path));
    }

    private boolean matchesPublicPath(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String basePath = pattern.substring(0, pattern.length() - 3);
            return path.equals(basePath) || path.startsWith(basePath + "/");
        }
        return path.equals(pattern);
    }

    private ServerHttpRequest removeUntrustedHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> TRUSTED_HEADERS.forEach(headers::remove))
                .build();
    }

    private ServerHttpRequest addTrustedInternalHeaders(ServerHttpRequest request) {
        return request.mutate()
                .header("X-Internal-Service", "api-gateway")
                .header("X-Internal-Token", internalServiceTokenProvider.generateCurrentToken())
                .build();
    }
    
    @Override
    public int getOrder() {
        return -300; // Sanitize identity headers before logging and rate limiting.
    }
}
