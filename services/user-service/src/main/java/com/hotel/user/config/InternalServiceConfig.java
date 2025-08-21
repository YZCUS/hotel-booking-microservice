package com.hotel.user.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@Getter
public class InternalServiceConfig {
    
    @Value("${app.internal.service-secret:${INTERNAL_SERVICE_SECRET:default-secret-change-in-production}}")
    private String serviceSecret;
    
    @Value("${app.internal.allowed-services:notification-service,booking-service,hotel-service}")
    private String[] allowedServices;
    
    @Value("${app.internal.token-header:X-Internal-Token}")
    private String tokenHeader;
    
    @Value("${app.internal.service-header:X-Internal-Service}")
    private String serviceHeader;
    
    public Set<String> getAllowedServicesSet() {
        return Set.of(allowedServices);
    }
    
    /**
     * Generate internal service token
     * Format: SHA-256(serviceName + secret + timestamp_rounded_to_minute)
     */
    public String generateExpectedToken(String serviceName, long timestampMinutes) {
        try {
            String data = serviceName + ":" + serviceSecret + ":" + timestampMinutes;
            return java.util.Base64.getEncoder().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(data.getBytes())
            ).substring(0, 32); // Use first 32 chars for readability
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}