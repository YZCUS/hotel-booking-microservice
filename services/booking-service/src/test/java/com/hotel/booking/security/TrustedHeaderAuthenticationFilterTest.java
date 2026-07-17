package com.hotel.booking.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TrustedHeaderAuthenticationFilterTest {

    private InternalServiceTokenService tokenService;
    private TrustedHeaderAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenService = new InternalServiceTokenService();
        ReflectionTestUtils.setField(tokenService, "serviceSecret", "test-shared-secret");
        filter = new TrustedHeaderAuthenticationFilter(tokenService);
        ReflectionTestUtils.setField(filter, "allowedServices",
                new String[]{"api-gateway", "hotel-service"});
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hotelServiceCannotEscalateRoleThroughUserHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service", "hotel-service");
        request.addHeader("X-Internal-Token", tokenService.generateToken("hotel-service"));
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_INTERNAL_HOTEL")));
        assertTrue(authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidTokenIsRejectedBeforeController() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service", "api-gateway");
        request.addHeader("X-Internal-Token", "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals(401, response.getStatus());
    }
}
