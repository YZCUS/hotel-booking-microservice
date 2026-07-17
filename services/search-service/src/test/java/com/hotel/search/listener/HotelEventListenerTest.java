package com.hotel.search.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.search.service.IndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class HotelEventListenerTest {

    private IndexService indexService;
    private HotelEventListener listener;

    @BeforeEach
    void setUp() {
        indexService = mock(IndexService.class);
        listener = new HotelEventListener(indexService);
    }

    @Test
    void handleHotelCreated_RethrowsIndexingFailure() {
        HotelEventListener.HotelCreatedEvent event = new HotelEventListener.HotelCreatedEvent();
        event.setHotelId(UUID.randomUUID());
        RuntimeException failure = new RuntimeException("index unavailable");
        doThrow(failure).when(indexService).indexHotel(any());

        assertThatThrownBy(() -> listener.handleHotelCreated(event)).isSameAs(failure);
    }

    @Test
    void handleHotelUpdated_RethrowsIndexingFailure() {
        HotelEventListener.HotelUpdatedEvent event = new HotelEventListener.HotelUpdatedEvent();
        event.setHotelId(UUID.randomUUID());
        RuntimeException failure = new RuntimeException("index unavailable");
        doThrow(failure).when(indexService).updateHotel(any());

        assertThatThrownBy(() -> listener.handleHotelUpdated(event)).isSameAs(failure);
    }

    @Test
    void handleHotelDeleted_RethrowsIndexingFailure() {
        HotelEventListener.HotelDeletedEvent event = new HotelEventListener.HotelDeletedEvent();
        event.setHotelId(UUID.randomUUID());
        RuntimeException failure = new RuntimeException("index unavailable");
        doThrow(failure).when(indexService).deleteHotel(event.getHotelId().toString());

        assertThatThrownBy(() -> listener.handleHotelDeleted(event)).isSameAs(failure);
    }

    @Test
    void eventContractIgnoresFutureProducerFields() throws Exception {
        UUID hotelId = UUID.randomUUID();
        String payload = """
                {"hotelId":"%s","name":"Hotel","futureField":"value"}
                """.formatted(hotelId);

        HotelEventListener.HotelCreatedEvent event = new ObjectMapper().readValue(
                payload, HotelEventListener.HotelCreatedEvent.class);

        assertThat(event.getHotelId()).isEqualTo(hotelId);
    }
}
