package com.hotel.user.util;

import com.hotel.user.config.InternalServiceConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalServiceUtil {
    
    private final InternalServiceConfig config;
    
    /**
     * Validate internal service authentication
     */
    public boolean validateInternalServiceCall(HttpServletRequest request) {
        try {
            String serviceName = request.getHeader(config.getServiceHeader());
            String providedToken = request.getHeader(config.getTokenHeader());
            
            if (serviceName == null || providedToken == null) {
                log.debug("Missing internal service headers");
                return false;
            }
            
            // Check if service is allowed
            if (!config.getAllowedServicesSet().contains(serviceName)) {
                log.warn("Unknown internal service attempted access: {}", serviceName);
                return false;
            }
            
            // Validate token (allow 2-minute window for clock skew)
            long currentTimeMinutes = System.currentTimeMillis() / (1000 * 60);
            
            for (int i = -1; i <= 1; i++) {
                String expectedToken = generateToken(serviceName, currentTimeMinutes + i);
                if (expectedToken.equals(providedToken)) {
                    log.debug("Valid internal service call from: {}", serviceName);
                    return true;
                }
            }
            
            log.warn("Invalid internal service token from: {}", serviceName);
            return false;
            
        } catch (Exception e) {
            log.error("Error validating internal service call", e);
            return false;
        }
    }
    
    /**
     * Generate token for internal service authentication
     */
    public String generateToken(String serviceName, long timestampMinutes) {
        try {
            String data = serviceName + ":" + config.getServiceSecret() + ":" + timestampMinutes;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash).substring(0, 32);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate internal service token", e);
        }
    }
    
    /**
     * Generate current token for a service (for testing/documentation)
     */
    public String generateCurrentToken(String serviceName) {
        long currentTimeMinutes = System.currentTimeMillis() / (1000 * 60);
        return generateToken(serviceName, currentTimeMinutes);
    }
}