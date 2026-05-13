package com.hotel.search.listener;

import com.hotel.search.service.IndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HotelEventListenerTest {

    private IndexService indexService;
    private HotelEventListener listener;

    @BeforeEach
    void setUp() {
        indexService = mock(IndexService.class);
        listener = new HotelEventListener(indexService);
    }

    @Test
    void handleHotelCreated_RethrowsIndexingFailureForRabbitRetry() {
        HotelEventListener.HotelCreatedEvent event = new HotelEventListener.HotelCreatedEvent();
        event.setHotelId(UUID.randomUUID());
        event.setName("Retry Hotel");
        org.mockito.Mockito.doThrow(new RuntimeException("Meilisearch unavailable"))
                .when(indexService).indexHotel(any());

        assertThrows(RuntimeException.class, () -> listener.handleHotelCreated(event));

        verify(indexService).indexHotel(any());
    }
}
