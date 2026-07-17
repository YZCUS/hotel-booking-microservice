package com.hotel.hotel.event;

import com.hotel.hotel.config.RabbitMQConfig;
import com.hotel.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final OutboxService outboxService;

    public void publishHotelCreated(HotelCreatedEvent event) {
        publish(RabbitMQConfig.HOTEL_CREATED_ROUTING_KEY, "hotel.created.v1", event);
    }

    public void publishHotelUpdated(HotelUpdatedEvent event) {
        publish(RabbitMQConfig.HOTEL_UPDATED_ROUTING_KEY, "hotel.updated.v1", event);
    }

    public void publishHotelDeleted(HotelDeletedEvent event) {
        publish(RabbitMQConfig.HOTEL_DELETED_ROUTING_KEY, "hotel.deleted.v1", event);
    }

    private void publish(String routingKey, String eventType, Object event) {
        log.info("Queueing hotel event with routing key {}", routingKey);
        outboxService.enqueue(RabbitMQConfig.HOTEL_EXCHANGE, routingKey, eventType, event);
    }
}
