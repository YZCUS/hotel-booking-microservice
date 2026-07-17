package com.hotel.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.booking.dto.BookingRequest;
import com.hotel.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingControllerTest {

    private BookingService bookingService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService)).build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void createBooking_MissingIdempotencyKey_ReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        BookingRequest request = BookingRequest.builder()
                .userId(userId)
                .roomTypeId(UUID.randomUUID())
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .guests(2)
                .build();

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }
}
