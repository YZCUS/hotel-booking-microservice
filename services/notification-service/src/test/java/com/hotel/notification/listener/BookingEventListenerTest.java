package com.hotel.notification.listener;

import com.hotel.notification.service.NotificationService;
import com.hotel.notification.service.NotificationService.BookingCancelledEvent;
import com.hotel.notification.service.NotificationService.BookingCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BookingEventListenerTest {

    private NotificationService notificationService;
    private BookingEventListener listener;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        listener = new BookingEventListener(notificationService);
    }

    @Test
    void handleBookingCreated_DelegatesToNotificationService() {
        BookingCreatedEvent event = new BookingCreatedEvent();
        event.setBookingId(UUID.randomUUID());

        listener.handleBookingCreated(event);

        verify(notificationService).sendBookingConfirmation(event);
    }

    @Test
    void handleBookingCreated_RethrowsFailureForRabbitRetry() {
        BookingCreatedEvent event = new BookingCreatedEvent();
        event.setBookingId(UUID.randomUUID());
        RuntimeException failure = new RuntimeException("mail down");
        doThrow(failure).when(notificationService).sendBookingConfirmation(event);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> listener.handleBookingCreated(event));

        verify(notificationService).sendBookingConfirmation(event);
        org.junit.jupiter.api.Assertions.assertSame(failure, thrown);
    }

    @Test
    void handleBookingCancelled_DelegatesToNotificationService() {
        BookingCancelledEvent event = new BookingCancelledEvent();
        event.setBookingId(UUID.randomUUID());

        listener.handleBookingCancelled(event);

        verify(notificationService).sendCancellationConfirmation(event);
    }
}
