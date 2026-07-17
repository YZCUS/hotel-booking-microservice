package com.hotel.user.security;

import com.hotel.user.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedUserReceivesAuthorityFromRoleClaim() throws Exception {
        when(jwtUtil.extractEmail("valid-token")).thenReturn("admin@example.com");
        when(jwtUtil.validateToken("valid-token", "admin@example.com")).thenReturn(true);
        when(jwtUtil.extractRole("valid-token")).thenReturn("ADMIN");
        MockHttpServletRequest request = requestWithToken("valid-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("admin@example.com", authentication.getPrincipal());
        assertEquals("ROLE_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void tokenWithoutRoleDoesNotAuthenticate() throws Exception {
        when(jwtUtil.extractEmail("legacy-token")).thenReturn("legacy@example.com");
        when(jwtUtil.validateToken("legacy-token", "legacy@example.com")).thenReturn(true);
        when(jwtUtil.extractRole("legacy-token")).thenReturn(null);

        filter.doFilter(requestWithToken("legacy-token"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
