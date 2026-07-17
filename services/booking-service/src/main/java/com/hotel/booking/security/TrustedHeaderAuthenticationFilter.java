package com.hotel.booking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TrustedHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> USER_ROLES = Set.of("USER", "HOTEL_STAFF", "ADMIN");
    private final InternalServiceTokenService tokenService;

    @Value("${app.internal.allowed-services:api-gateway,hotel-service}")
    private String[] allowedServices;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String service = request.getHeader("X-Internal-Service");
        String token = request.getHeader("X-Internal-Token");
        if (service == null && token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!Arrays.asList(allowedServices).contains(service) || !tokenService.isValid(service, token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service credentials");
            return;
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if ("hotel-service".equals(service)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_INTERNAL_HOTEL"));
        } else if ("api-gateway".equals(service)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_GATEWAY"));
            String role = request.getHeader("X-User-Role");
            if (role != null && USER_ROLES.contains(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }

        String principal = request.getHeader("X-User-Id");
        if (principal == null || principal.isBlank()) {
            principal = service;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, token, authorities));
        filterChain.doFilter(request, response);
    }
}
