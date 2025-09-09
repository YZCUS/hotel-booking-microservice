package com.hotel.notification.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMQHealthIndicator implements HealthIndicator {

    private final ConnectionFactory connectionFactory;

    @Override
    public Health health() {
        try {
            // Attempt to create a new connection
            this.connectionFactory.createConnection().close();
            return Health.up()
                    .withDetail("rabbitmq", "Available")
                    .withDetail("host", connectionFactory.getHost())
                    .withDetail("port", connectionFactory.getPort())
                    .build();
        } catch (Exception e) {
            log.error("RabbitMQ health check failed for notification-service", e);
            return Health.down(e)
                    .withDetail("rabbitmq", "Unavailable")
                    .withDetail("status", "Connection failed")
                    .build();
        }
    }
}