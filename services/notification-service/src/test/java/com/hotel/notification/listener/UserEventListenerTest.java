package com.hotel.notification.listener;

import com.hotel.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UserEventListenerTest {

    @Test
    void handleUserRegistered_RethrowsEmailFailureForBrokerRetry() {
        NotificationService notificationService = mock(NotificationService.class);
        UserEventListener listener = new UserEventListener(notificationService);
        NotificationService.UserRegisteredEvent event = new NotificationService.UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());
        event.setEmail("guest@example.com");
        RuntimeException failure = new RuntimeException("smtp unavailable");
        doThrow(failure).when(notificationService).sendWelcomeMessage(event);

        assertThatThrownBy(() -> listener.handleUserRegistered(event)).isSameAs(failure);
    }

    @Test
    void handleUserRegistered_RejectsInvalidPayloadToDeadLetterQueue() {
        NotificationService notificationService = mock(NotificationService.class);
        UserEventListener listener = new UserEventListener(notificationService);
        NotificationService.UserRegisteredEvent event = new NotificationService.UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());

        assertThatThrownBy(() -> listener.handleUserRegistered(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notificationService);
    }
}
