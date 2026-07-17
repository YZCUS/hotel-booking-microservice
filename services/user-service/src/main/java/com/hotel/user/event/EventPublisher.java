package com.hotel.user.event;

import com.hotel.user.config.RabbitMQConfig;
import com.hotel.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final OutboxService outboxService;

    public void publishUserRegistered(UserRegisteredEvent event) {
        log.info("Queueing user registered event for user: {}", event.getUserId());
        outboxService.enqueue(
                RabbitMQConfig.USER_EXCHANGE,
                RabbitMQConfig.USER_REGISTERED_ROUTING_KEY,
                "user.registered.v1",
                event);
    }
}
