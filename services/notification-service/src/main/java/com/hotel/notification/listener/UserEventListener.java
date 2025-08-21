package com.hotel.notification.listener;

import com.hotel.notification.service.NotificationService;
import com.hotel.notification.service.NotificationService.UserRegisteredEvent;
import com.hotel.notification.exception.ServiceCommunicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.web.reactive.function.client.WebClientException;

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
            
        } catch (ServiceCommunicationException e) {
            // 服務通信失敗 - 這是臨時性錯誤，應該重試
            log.warn("Service communication failed for user {}, will retry: {}", 
                    event.getUserId(), e.getMessage());
            throw e; // 重新拋出以觸發 RabbitMQ 重試機制
            
        } catch (WebClientException e) {
            // 網路或外部服務錯誤 - 臨時性錯誤，應該重試
            log.warn("External service error for user {}, will retry: {}", 
                    event.getUserId(), e.getMessage());
            throw e; // 重新拋出以觸發 RabbitMQ 重試機制
            
        } catch (DataAccessException e) {
            // 資料庫錯誤 - 可能是臨時性錯誤，應該重試
            log.warn("Database error for user {}, will retry: {}", 
                    event.getUserId(), e.getMessage());
            throw e; // 重新拋出以觸發 RabbitMQ 重試機制
            
        } catch (IllegalArgumentException | NullPointerException e) {
            // 資料驗證錯誤 - 永久性錯誤，不需要重試
            log.error("Invalid event data for user {}, discarding message: {}", 
                    event.getUserId(), e.getMessage());
            // 不重新拋出異常，讓消息被確認並丟棄
            
        } catch (Exception e) {
            // 其他未知錯誤 - 記錄詳細信息但不重試（歡迎郵件不是關鍵業務）
            log.error("Unexpected error processing welcome email for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            // 對於歡迎郵件，我們選擇不重試未知錯誤以避免無限循環
            // 在生產環境中，可以考慮將這些發送到專門的監控隊列
        }
    }
}