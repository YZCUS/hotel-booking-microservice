package com.hotel.notification.listener;

import com.hotel.notification.exception.ServiceCommunicationException;
import com.hotel.notification.service.NotificationService;
import com.hotel.notification.service.NotificationService.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
        ServiceCommunicationException failure = new ServiceCommunicationException("user-service unavailable");
        doThrow(failure).when(notificationService).sendWelcomeMessage(event);

        ServiceCommunicationException thrown = assertThrows(ServiceCommunicationException.class,
                () -> listener.handleUserRegistered(event));

        assertSame(failure, thrown);
    }

    @Test
    void handleUserRegistered_DiscardsInvalidEventDataWithoutRetry() {
        UserRegisteredEvent event = userRegisteredEvent();
        doThrow(new IllegalArgumentException("missing email"))
                .when(notificationService).sendWelcomeMessage(event);

        assertDoesNotThrow(() -> listener.handleUserRegistered(event));

        verify(notificationService).sendWelcomeMessage(event);
    }

    private UserRegisteredEvent userRegisteredEvent() {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(UUID.randomUUID());
        event.setEmail("alice@example.com");
        event.setFullName("Alice Chen");
        return event;
    }
}
