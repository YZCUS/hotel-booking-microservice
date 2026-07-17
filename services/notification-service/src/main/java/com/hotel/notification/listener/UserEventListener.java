package com.hotel.notification.listener;

import com.hotel.notification.config.RabbitMQConfig;
import com.hotel.notification.service.NotificationService;
import com.hotel.notification.service.NotificationService.UserRegisteredEvent;
import com.hotel.notification.exception.ServiceCommunicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.web.reactive.function.client.WebClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {
    
    private final NotificationService notificationService;
    
    @RabbitListener(queues = RabbitMQConfig.USER_REGISTERED_QUEUE)
    public void handleUserRegistered(UserRegisteredEvent event) {
        Object userId = event == null ? null : event.getUserId();
        log.info("Received user registered event: {}", userId);
        
        try {
            validateEvent(event);
            notificationService.sendWelcomeMessage(event);
            log.info("Successfully processed user registered event: {}", userId);
            
        } catch (ServiceCommunicationException e) {
            // 服務通信失敗 - 這是臨時性錯誤，應該重試
            log.warn("Service communication failed for user {}, will retry: {}", 
                    userId, e.getMessage());
            throw e; // 重新拋出以觸發 RabbitMQ 重試機制
            
        } catch (WebClientException e) {
            // 網路或外部服務錯誤 - 臨時性錯誤，應該重試
            log.warn("External service error for user {}, will retry: {}", 
                    userId, e.getMessage());
            throw e; // 重新拋出以觸發 RabbitMQ 重試機制
            
        } catch (DataAccessException e) {
            // 資料庫錯誤 - 可能是臨時性錯誤，應該重試
            log.warn("Database error for user {}, will retry: {}", 
                    userId, e.getMessage());
            throw e; // 重新拋出以觸發 RabbitMQ 重試機制
            
        } catch (IllegalArgumentException | NullPointerException e) {
            log.error("Invalid event data for user {}, sending to dead letter queue: {}",
                    userId, e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid user registered event", e);
            
        } catch (Exception e) {
            log.error("Unexpected error processing welcome email for user {}: {}", 
                    userId, e.getMessage(), e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Failed to process welcome email", e);
        }
    }

    private void validateEvent(UserRegisteredEvent event) {
        if (event == null || event.getUserId() == null
                || event.getEmail() == null || event.getEmail().isBlank()) {
            throw new IllegalArgumentException("User id and email are required");
        }
    }
}
