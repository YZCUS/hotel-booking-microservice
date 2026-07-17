package com.hotel.hotel.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalServiceTokenServiceTest {

    @Test
    void generateToken_MatchesSharedContractVector() {
        InternalServiceTokenService tokenService = new InternalServiceTokenService();
        ReflectionTestUtils.setField(tokenService, "serviceSecret", "test-shared-secret");

        assertEquals("1FBydQLsmqYxAv7qEc1acIez5Pdmc/37",
                tokenService.generateToken("api-gateway", 30_000_000L));
    }
}
