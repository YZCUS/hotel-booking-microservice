package com.hotel.booking.event;

import com.hotel.booking.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {
    
    private final AmqpTemplate amqpTemplate;
    
    public void publishBookingCreated(BookingCreatedEvent event) {
        try {
            log.info("Publishing booking created event for booking: {}", event.getBookingId());
            amqpTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CREATED_ROUTING_KEY,
                event
            );
            log.debug("Successfully published booking created event: {}", event);
        } catch (Exception e) {
            log.error("Failed to publish booking created event: {}", event, e);
            throw new RuntimeException("Failed to publish booking created event", e);
        }
    }
    
    public void publishBookingCancelled(BookingCancelledEvent event) {
        try {
            log.info("Publishing booking cancelled event for booking: {}", event.getBookingId());
            amqpTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CANCELLED_ROUTING_KEY,
                event
            );
            log.debug("Successfully published booking cancelled event: {}", event);
        } catch (Exception e) {
            log.error("Failed to publish booking cancelled event: {}", event, e);
            throw new RuntimeException("Failed to publish booking cancelled event", e);
        }
    }
}