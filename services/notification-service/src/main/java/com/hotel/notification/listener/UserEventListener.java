package com.hotel.notification.listener;

import com.hotel.notification.service.NotificationService;
import com.hotel.notification.service.NotificationService.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {
    
    private final NotificationService notificationService;
    
    @RabbitListener(queues = "user.registered.queue")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received user registered event: {}", event.getUserId());
        
        try {
            notificationService.sendWelcomeMessage(event);
            log.info("Successfully processed user registered event: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process user registered event: {}", event.getUserId(), e);
            // Welcome emails are not critical, so we don't re-throw the exception
            // This prevents the message from being retried or sent to DLQ
        }
    }
}