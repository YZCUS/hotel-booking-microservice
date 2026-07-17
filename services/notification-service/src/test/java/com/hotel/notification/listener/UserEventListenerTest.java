package com.hotel.notification.listener;

import com.hotel.notification.exception.ServiceCommunicationException;
import com.hotel.notification.service.NotificationService;
import com.hotel.notification.service.NotificationService.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UserEventListenerTest {

    private NotificationService notificationService;
    private UserEventListener listener;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        listener = new UserEventListener(notificationService);
    }

    @Test
    void handleUserRegistered_DelegatesToNotificationService() {
        UserRegisteredEvent event = userRegisteredEvent();

        listener.handleUserRegistered(event);

        verify(notificationService).sendWelcomeMessage(event);
    }

    @Test
    void handleUserRegistered_RethrowsTransientServiceFailuresForRetry() {
        UserRegisteredEvent event = userRegisteredEvent();
        ServiceCommunicationException failure =
                new ServiceCommunicationException("user-service unavailable");
        doThrow(failure).when(notificationService).sendWelcomeMessage(event);

        assertThatThrownBy(() -> listener.handleUserRegistered(event)).isSameAs(failure);
    }

    @Test
    void handleUserRegistered_RethrowsEmailFailureForBrokerRetry() {
        UserRegisteredEvent event = userRegisteredEvent();
        RuntimeException failure = new RuntimeException("smtp unavailable");
        doThrow(failure).when(notificationService).sendWelcomeMessage(event);

        assertThatThrownBy(() -> listener.handleUserRegistered(event)).isSameAs(failure);
    }

    @Test
    void handleUserRegistered_RejectsInvalidPayloadToDeadLetterQueue() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());

        assertThatThrownBy(() -> listener.handleUserRegistered(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notificationService);
    }

    private UserRegisteredEvent userRegisteredEvent() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());
        event.setEmail("alice@example.com");
        event.setFullName("Alice Chen");
        return event;
    }
}
