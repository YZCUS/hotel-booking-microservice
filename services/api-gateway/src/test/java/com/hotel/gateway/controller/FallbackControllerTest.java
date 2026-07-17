package com.hotel.gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class FallbackControllerTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new FallbackController()).build();
    }

    @Test
    void bookingFallbackHandlesWriteMethods() {
        webTestClient.post()
                .uri("/fallback/bookings")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.service").isEqualTo("booking-service");
    }

    @Test
    void hotelFallbackHandlesWriteMethods() {
        webTestClient.delete()
                .uri("/fallback/hotels")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.service").isEqualTo("hotel-service");
    }
}
