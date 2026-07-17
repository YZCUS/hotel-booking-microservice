package com.hotel.user.controller;

import com.hotel.user.exception.AccessDeniedException;
import com.hotel.user.service.UserService;
import com.hotel.user.util.InternalServiceUtil;
import com.hotel.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerSecurityTest {

    private final UserService userService = mock(UserService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final UserController controller = new UserController(
            userService, jwtUtil, mock(InternalServiceUtil.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gatewayEndUserRequestDoesNotReceiveInternalServiceBypass() {
        UUID currentUserId = UUID.randomUUID();
        UUID requestedUserId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Authenticated")).thenReturn("true");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtUtil.extractUserId("valid-token")).thenReturn(currentUserId);
        when(jwtUtil.extractRole("valid-token")).thenReturn("USER");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "INTERNAL_SERVICE_api-gateway",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))));

        assertThrows(AccessDeniedException.class,
                () -> controller.getUserById(requestedUserId, request));
        verify(userService, never()).getUserById(requestedUserId);
    }
}
