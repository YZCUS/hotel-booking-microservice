package com.hotel.hotel.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Component
public class InternalServiceTokenService {

    private static final long ALLOWED_CLOCK_SKEW_MINUTES = 1;

    @Value("${app.internal.service-secret:${INTERNAL_SERVICE_SECRET:secure-shared-secret-change-in-production}}")
    private String serviceSecret;

    public String generateToken(String serviceName) {
        return generateToken(serviceName, Instant.now().getEpochSecond() / 60);
    }

    String generateToken(String serviceName, long epochMinute) {
        try {
            String payload = serviceName + ":" + serviceSecret + ":" + epochMinute;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public boolean isValid(String serviceName, String suppliedToken) {
        if (serviceName == null || suppliedToken == null) {
            return false;
        }
        long currentMinute = Instant.now().getEpochSecond() / 60;
        for (long offset = -ALLOWED_CLOCK_SKEW_MINUTES;
             offset <= ALLOWED_CLOCK_SKEW_MINUTES; offset++) {
            byte[] expected = generateToken(serviceName, currentMinute + offset)
                    .getBytes(StandardCharsets.UTF_8);
            byte[] supplied = suppliedToken.getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, supplied)) {
                return true;
            }
        }
        return false;
    }
}
