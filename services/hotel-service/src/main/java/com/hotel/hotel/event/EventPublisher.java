package com.hotel.hotel.event;

import com.hotel.hotel.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final AmqpTemplate amqpTemplate;

    public void publishHotelCreated(HotelCreatedEvent event) {
        publish(RabbitMQConfig.HOTEL_CREATED_ROUTING_KEY, event);
    }

    public void publishHotelUpdated(HotelUpdatedEvent event) {
        publish(RabbitMQConfig.HOTEL_UPDATED_ROUTING_KEY, event);
    }

    public void publishHotelDeleted(HotelDeletedEvent event) {
        publish(RabbitMQConfig.HOTEL_DELETED_ROUTING_KEY, event);
    }

    private void publish(String routingKey, Object event) {
        log.info("Publishing hotel event with routing key {}", routingKey);
        amqpTemplate.convertAndSend(RabbitMQConfig.HOTEL_EXCHANGE, routingKey, event);
    }
}
