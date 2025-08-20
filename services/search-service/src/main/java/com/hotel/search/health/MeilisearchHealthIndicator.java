package com.hotel.search.health;

import com.meilisearch.sdk.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeilisearchHealthIndicator implements HealthIndicator {
    
    private final Client meilisearchClient;
    
    @Override
    public Health health() {
        try {
            // Try to get version to check if Meilisearch is responding
            var version = meilisearchClient.getVersion();
            
            return Health.up()
                .withDetail("meilisearch", "Available")
                .withDetail("version", version)
                .withDetail("status", "Connected")
                .build();
                
        } catch (Exception e) {
            log.error("Meilisearch health check failed", e);
            return Health.down(e)
                .withDetail("meilisearch", "Unavailable")
                .withDetail("status", "Connection failed")
                .build();
        }
    }
}