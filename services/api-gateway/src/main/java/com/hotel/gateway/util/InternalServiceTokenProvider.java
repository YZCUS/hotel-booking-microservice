package com.hotel.gateway.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class InternalServiceTokenProvider {

    private static final String SERVICE_NAME = "api-gateway";

    private final String serviceSecret;

    public InternalServiceTokenProvider(@Value("${app.internal.service-secret}") String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    public String generateCurrentToken() {
        long timestampMinutes = System.currentTimeMillis() / (1000 * 60);
        return generateToken(SERVICE_NAME, timestampMinutes);
    }

    public String generateToken(String serviceName, long timestampMinutes) {
        try {
            String data = serviceName + ":" + serviceSecret + ":" + timestampMinutes;
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
