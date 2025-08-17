package com.hotel.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class CorsConfig {
    
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String allowedOrigins;
    
    @Bean
    public CorsWebFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);
        
        // Set allowed origins
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        origins.forEach(origin -> {
            config.addAllowedOrigin(origin.trim());
            log.info("Added allowed CORS origin: {}", origin.trim());
        });
        
        // Allow all headers
        config.addAllowedHeader("*");
        
        // Allow all methods
        config.addAllowedMethod("*");
        
        // Expose headers that frontend might need
        config.addExposedHeader("X-RateLimit-Limit");
        config.addExposedHeader("X-RateLimit-Remaining");
        config.addExposedHeader("X-Total-Count");
        config.addExposedHeader("Authorization");
        
        // Set max age for preflight requests
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        log.info("CORS configuration initialized with allowed origins: {}", origins);
        
        return new CorsWebFilter(source);
    }
}