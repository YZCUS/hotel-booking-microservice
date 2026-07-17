package com.hotel.user.util;

import com.hotel.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "user-service-test-secret-key-with-more-than-64-characters-1234567890");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
    }

    @Test
    void generatedTokenContainsPersistedUserRole() {
        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateToken("admin@example.com", userId, "ADMIN");

        assertTrue(jwtUtil.validateToken(token));
        assertEquals(userId, jwtUtil.extractUserId(token));
        assertEquals("ADMIN", jwtUtil.extractRole(token));
    }

    @Test
    void newUsersDefaultToUserRole() {
        User user = User.builder()
                .email("new@example.com")
                .passwordHash("hash")
                .build();

        assertEquals("USER", user.getRole());
    }
}
