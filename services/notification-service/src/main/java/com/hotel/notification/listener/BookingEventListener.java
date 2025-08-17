package com.hotel.notification.listener;

import com.hotel.notification.service.NotificationService;
import com.hotel.notification.service.NotificationService.BookingCreatedEvent;
import com.hotel.notification.service.NotificationService.BookingCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {
    
    private final NotificationService notificationService;
    
    @RabbitListener(queues = "booking.created.queue")
    public void handleBookingCreated(BookingCreatedEvent event) {
        log.info("Received booking created event: {}", event.getBookingId());
        
        try {
            notificationService.sendBookingConfirmation(event);
            log.info("Successfully processed booking created event: {}", event.getBookingId());
        } catch (Exception e) {
            log.error("Failed to process booking created event: {}", event.getBookingId(), e);
            // In production, you might want to:
            // 1. Send to dead letter queue
            // 2. Store in database for retry
            // 3. Send alert to monitoring system
            throw e; // This will trigger RabbitMQ retry mechanism
        }
    }
    
    @RabbitListener(queues = "booking.cancelled.queue")
    public void handleBookingCancelled(BookingCancelledEvent event) {
        log.info("Received booking cancelled event: {}", event.getBookingId());
        
        try {
            notificationService.sendCancellationConfirmation(event);
            log.info("Successfully processed booking cancelled event: {}", event.getBookingId());
        } catch (Exception e) {
            log.error("Failed to process booking cancelled event: {}", event.getBookingId(), e);
            throw e; // This will trigger RabbitMQ retry mechanism
        }
    }
}