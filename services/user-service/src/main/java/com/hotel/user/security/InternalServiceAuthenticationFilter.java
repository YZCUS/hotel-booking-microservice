package com.hotel.user.security;

import com.hotel.user.util.InternalServiceUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {
    
    private final InternalServiceUtil internalServiceUtil;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Check if this is an internal service call
        if (internalServiceUtil.validateInternalServiceCall(request)) {
            String serviceName = request.getHeader("X-Internal-Service");
            log.debug("Authenticating internal service: {}", serviceName);
            
            // Create authentication token for internal service
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(
                    "INTERNAL_SERVICE_" + serviceName, 
                    null, 
                    List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
                );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            log.debug("Internal service {} authenticated successfully", serviceName);
        }
        
        filterChain.doFilter(request, response);
    }
}