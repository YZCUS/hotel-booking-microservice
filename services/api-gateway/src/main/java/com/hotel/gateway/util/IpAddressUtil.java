package com.hotel.gateway.util;

import org.springframework.http.server.reactive.ServerHttpRequest;


public class IpAddressUtil {
    public static String getClientIpAddress(ServerHttpRequest request) {
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
        return request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
}
