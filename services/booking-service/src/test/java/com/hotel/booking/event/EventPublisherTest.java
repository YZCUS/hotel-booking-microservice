package com.hotel.booking.event;

import com.hotel.outbox.OutboxService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class EventPublisherTest {

    @Test
    void bookingEventsAreStoredWithStableOutboxTypes() {
        OutboxService outboxService = mock(OutboxService.class);
        EventPublisher publisher = new EventPublisher(outboxService);
        BookingCreatedEvent created = BookingCreatedEvent.builder().bookingId(UUID.randomUUID()).build();
        BookingCancelledEvent cancelled = BookingCancelledEvent.builder().bookingId(UUID.randomUUID()).build();

        publisher.publishBookingCreated(created);
        publisher.publishBookingCancelled(cancelled);

        assertThat(created.getEventType()).isEqualTo("BOOKING_CREATED");
        assertThat(cancelled.getEventType()).isEqualTo("BOOKING_CANCELLED");
        verify(outboxService).enqueue(
                eq("booking.exchange"), eq("booking.created.v2"), eq("booking.created.v1"), same(created));
        verify(outboxService).enqueue(
                eq("booking.exchange"), eq("booking.cancelled.v2"), eq("booking.cancelled.v1"), same(cancelled));
    }
}
