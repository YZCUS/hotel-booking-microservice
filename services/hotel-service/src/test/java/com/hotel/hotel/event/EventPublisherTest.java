package com.hotel.hotel.event;

import com.hotel.outbox.OutboxService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class EventPublisherTest {

    @Test
    void hotelEventsAreStoredWithStableOutboxTypes() {
        OutboxService outboxService = mock(OutboxService.class);
        EventPublisher publisher = new EventPublisher(outboxService);
        HotelCreatedEvent created = HotelCreatedEvent.builder().hotelId(UUID.randomUUID()).build();
        HotelUpdatedEvent updated = HotelUpdatedEvent.builder().hotelId(UUID.randomUUID()).build();
        HotelDeletedEvent deleted = HotelDeletedEvent.builder().hotelId(UUID.randomUUID()).build();

        publisher.publishHotelCreated(created);
        publisher.publishHotelUpdated(updated);
        publisher.publishHotelDeleted(deleted);

        verify(outboxService).enqueue(
                eq("hotel.exchange"), eq("hotel.created.v2"), eq("hotel.created.v1"), same(created));
        verify(outboxService).enqueue(
                eq("hotel.exchange"), eq("hotel.updated.v2"), eq("hotel.updated.v1"), same(updated));
        verify(outboxService).enqueue(
                eq("hotel.exchange"), eq("hotel.deleted.v2"), eq("hotel.deleted.v1"), same(deleted));
    }
}
