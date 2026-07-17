package com.hotel.booking.event;

import com.hotel.booking.config.RabbitMQConfig;
import com.hotel.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {
    
    private final OutboxService outboxService;
    
    public void publishBookingCreated(BookingCreatedEvent event) {
        log.info("Queueing booking created event for booking: {}", event.getBookingId());
        outboxService.enqueue(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CREATED_ROUTING_KEY,
                "booking.created.v1",
                event
        );
    }
    
    public void publishBookingCancelled(BookingCancelledEvent event) {
        log.info("Queueing booking cancelled event for booking: {}", event.getBookingId());
        outboxService.enqueue(
                RabbitMQConfig.BOOKING_EXCHANGE,
                RabbitMQConfig.BOOKING_CANCELLED_ROUTING_KEY,
                "booking.cancelled.v1",
                event
        );
    }
}
