package com.hotel.gateway.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalServiceTokenProviderTest {

    @Test
    void matchesSharedInternalTokenTestVector() {
        InternalServiceTokenProvider provider = new InternalServiceTokenProvider("test-shared-secret");

        String token = provider.generateToken("api-gateway", 30_000_000L);

        assertEquals("1FBydQLsmqYxAv7qEc1acIez5Pdmc/37", token);
    }
}
