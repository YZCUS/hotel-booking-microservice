package com.hotel.gateway.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpAddressUtilTest {

    @Test
    void getClientIpAddress_UsesFirstForwardedForAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/hotels")
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.5")
                .header("X-Real-IP", "198.51.100.8")
                .build();

        assertEquals("203.0.113.10", IpAddressUtil.getClientIpAddress(request));
    }

    @Test
    void getClientIpAddress_UsesRealIpWhenForwardedForMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/hotels")
                .header("X-Real-IP", "198.51.100.8")
                .build();

        assertEquals("198.51.100.8", IpAddressUtil.getClientIpAddress(request));
    }

    @Test
    void getClientIpAddress_ReturnsUnknownWithoutRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/hotels").build();

        assertEquals("unknown", IpAddressUtil.getClientIpAddress(request));
    }
}
