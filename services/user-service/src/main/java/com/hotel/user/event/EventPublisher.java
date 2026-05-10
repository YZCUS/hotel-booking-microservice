package com.hotel.user.event;

import com.hotel.user.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final AmqpTemplate amqpTemplate;

    public void publishUserRegistered(UserRegisteredEvent event) {
        log.info("Publishing user registered event for user: {}", event.getUserId());
        amqpTemplate.convertAndSend(
                RabbitMQConfig.USER_EXCHANGE,
                RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                event);
    }
}
