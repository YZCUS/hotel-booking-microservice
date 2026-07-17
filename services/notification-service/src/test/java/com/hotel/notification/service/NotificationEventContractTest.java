package com.hotel.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void bookingEventIgnoresProducerMetadataAndFutureFields() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String payload = """
                {"bookingId":"%s","eventType":"BOOKING_CREATED","futureField":"value"}
                """.formatted(bookingId);

        NotificationService.BookingCreatedEvent event = objectMapper.readValue(
                payload, NotificationService.BookingCreatedEvent.class);

        assertThat(event.getBookingId()).isEqualTo(bookingId);
    }

    @Test
    void userEventIgnoresFutureFields() throws Exception {
        UUID userId = UUID.randomUUID();
        String payload = """
                {"userId":"%s","email":"guest@example.com","futureField":"value"}
                """.formatted(userId);

        NotificationService.UserRegisteredEvent event = objectMapper.readValue(
                payload, NotificationService.UserRegisteredEvent.class);

        assertThat(event.getUserId()).isEqualTo(userId);
    }
}
