package com.hotel.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtUtilTest {

    private static final String SECRET =
            "gateway-test-secret-key-with-more-than-64-characters-1234567890abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET);
    }

    @Test
    void expiredTokenIsInvalid() {
        String token = token(new Date(System.currentTimeMillis() - 1_000), "USER");

        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void tokenWithoutRoleDoesNotReceiveDefaultRole() {
        String token = token(new Date(System.currentTimeMillis() + 60_000), null);

        assertNull(jwtUtil.extractRole(token));
    }

    @Test
    void validatedClaimsRetainVerifiedIdentity() {
        var claims = jwtUtil.parseValidatedClaims(token(new Date(System.currentTimeMillis() + 60_000), "USER"));

        assertNotNull(claims);
        assertEquals("user@example.com", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
        assertNotNull(UUID.fromString(claims.get("userId", String.class)));
    }

    @Test
    void expiredAndMalformedTokensDoNotExposeClaims() {
        assertNull(jwtUtil.parseValidatedClaims(token(new Date(System.currentTimeMillis() - 1_000), "USER")));
        assertNull(jwtUtil.parseValidatedClaims("not-a-jwt"));
    }

    @Test
    void tokenSignedWithAnotherKeyDoesNotExposeClaims() {
        String token = token(new Date(System.currentTimeMillis() + 60_000), "ADMIN");
        ReflectionTestUtils.setField(jwtUtil, "secretKey", SECRET + "different-key");

        assertNull(jwtUtil.parseValidatedClaims(token));
    }

    @Test
    void tokenWithoutExpirationIsRejected() {
        assertNull(jwtUtil.parseValidatedClaims(token(null, "USER")));
    }

    private String token(Date expiration, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .setSubject("user@example.com")
                .claim("userId", UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(expiration);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key, SignatureAlgorithm.HS512).compact();
    }
}
