package com.hotel.user.event;

import com.hotel.outbox.OutboxService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class EventPublisherTest {

    @Test
    void userRegisteredIsStoredInTransactionalOutbox() {
        OutboxService outboxService = mock(OutboxService.class);
        EventPublisher publisher = new EventPublisher(outboxService);
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(UUID.randomUUID())
                .email("guest@example.com")
                .build();

        publisher.publishUserRegistered(event);

        verify(outboxService).enqueue(
                eq("user.exchange"), eq("user.registered.v2"), eq("user.registered.v1"), same(event));
    }
}
