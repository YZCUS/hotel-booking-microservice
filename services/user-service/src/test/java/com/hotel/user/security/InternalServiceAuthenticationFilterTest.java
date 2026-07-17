package com.hotel.user.security;

import com.hotel.user.util.InternalServiceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalServiceAuthenticationFilterTest {

    private final InternalServiceUtil internalServiceUtil = mock(InternalServiceUtil.class);
    private final InternalServiceAuthenticationFilter filter =
            new InternalServiceAuthenticationFilter(internalServiceUtil);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gatewayEndUserTrafficIsLeftForJwtAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service", "api-gateway");
        request.addHeader("X-Authenticated", "true");
        when(internalServiceUtil.validateInternalServiceCall(request)).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void nonUserInternalTrafficReceivesInternalServiceAuthority() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service", "notification-service");
        when(internalServiceUtil.validateInternalServiceCall(request)).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("ROLE_INTERNAL_SERVICE", SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities().iterator().next().getAuthority());
    }
}
