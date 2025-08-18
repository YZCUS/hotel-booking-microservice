package com.hotel.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig {
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints
                .pathMatchers(
                    "/api/v1/auth/**",  // Auth is public
                    "/api/v1/hotels/**",  // Hotel browsing is public
                    "/api/v1/search/**",  // Search is public
                    "/api/v1/inventory/check-availability", // Availability check is public
                    "/health/**",
                    "/actuator/**",
                    "/fallback/**"
                ).permitAll()
                // All other endpoints require authentication
                .anyExchange().authenticated()
            )
            .build();
    }
}