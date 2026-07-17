package com.hotel.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxServiceTest {

    @Test
    void enqueueSerializesAndStoresEvent() throws Exception {
        OutboxStore store = mock(OutboxStore.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        OutboxService service = new OutboxService(store, objectMapper);
        Map<String, String> event = Map.of("bookingId", "booking-1");
        UUID eventId = UUID.randomUUID();
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"bookingId\":\"booking-1\"}");
        when(store.enqueue(
                "booking.exchange",
                "booking.created",
                "booking.created.v1",
                "{\"bookingId\":\"booking-1\"}"))
                .thenReturn(eventId);

        assertThat(service.enqueue(
                "booking.exchange", "booking.created", "booking.created.v1", event))
                .isEqualTo(eventId);
        verify(store).enqueue(
                "booking.exchange",
                "booking.created",
                "booking.created.v1",
                "{\"bookingId\":\"booking-1\"}");
    }

    @Test
    void enqueueRequiresAnExistingBusinessTransaction() throws Exception {
        Transactional transactional = OutboxService.class
                .getMethod("enqueue", String.class, String.class, String.class, Object.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
