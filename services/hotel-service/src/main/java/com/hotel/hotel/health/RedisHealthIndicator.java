package com.hotel.hotel.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();

            if ("PONG".equals(pong)) {
                return Health.up()
                        .withDetail("redis", "Available")
                        .withDetail("status", "Connected")
                        .build();
            }
        } catch (Exception e) {
            log.error("Redis health check failed for hotel-service", e);
            return Health.down(e)
                    .withDetail("redis", "Unavailable")
                    .withDetail("status", "Connection failed")
                    .build();
        }
        return Health.down()
                .withDetail("redis", "Unavailable")
                .withDetail("status", "Ping failed")
                .build();
    }
}