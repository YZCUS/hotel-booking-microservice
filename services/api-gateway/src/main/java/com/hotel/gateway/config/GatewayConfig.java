package com.hotel.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // User Service routes - Authentication (no JWT required)
            .route("user-service-auth", r -> r
                .path("/api/v1/auth/**")
                .filters(f -> f
                    .rewritePath("/api/v1/auth/(?<segment>.*)", "/api/v1/auth/${segment}")
                    .addRequestHeader("X-Service", "user-service")
                    .addRequestHeader("X-Gateway", "api-gateway"))
                .uri("http://user-service:8081"))
            
            // User Service routes (JWT required)
            .route("user-service", r -> r
                .path("/api/v1/users/**")
                .filters(f -> f
                    .rewritePath("/api/v1/users/(?<segment>.*)", "/api/v1/users/${segment}")
                    .addRequestHeader("X-Service", "user-service")
                    .addRequestHeader("X-Gateway", "api-gateway"))
                .uri("http://user-service:8081"))
            
            // Hotel Service routes (public read, JWT for write)
            .route("hotel-service", r -> r
                .path("/api/v1/hotels/**")
                .filters(f -> f
                    .rewritePath("/api/v1/hotels/(?<segment>.*)", "/api/v1/hotels/${segment}")
                    .addRequestHeader("X-Service", "hotel-service")
                    .addRequestHeader("X-Gateway", "api-gateway")
                    .circuitBreaker(config -> config
                        .setName("hotelServiceCB")
                        .setFallbackUri("forward:/fallback/hotels")))
                .uri("http://hotel-service:8082"))
            
            // Booking Service routes (JWT required)
            .route("booking-service", r -> r
                .path("/api/v1/bookings/**")
                .filters(f -> f
                    .rewritePath("/api/v1/bookings/(?<segment>.*)", "/api/v1/bookings/${segment}")
                    .addRequestHeader("X-Service", "booking-service")
                    .addRequestHeader("X-Gateway", "api-gateway")
                    .circuitBreaker(config -> config
                        .setName("bookingServiceCB")
                        .setFallbackUri("forward:/fallback/bookings")))
                .uri("http://booking-service:8083"))
            
            // Inventory Service routes (for availability checks)
            .route("inventory-service", r -> r
                .path("/api/v1/inventory/**")
                .filters(f -> f
                    .rewritePath("/api/v1/inventory/(?<segment>.*)", "/api/v1/inventory/${segment}")
                    .addRequestHeader("X-Service", "booking-service")
                    .addRequestHeader("X-Gateway", "api-gateway"))
                .uri("http://booking-service:8083"))
            
            // Search Service routes (public)
            .route("search-service", r -> r
                .path("/api/v1/search/**")
                .filters(f -> f
                    .rewritePath("/api/v1/search/(?<segment>.*)", "/api/v1/search/${segment}")
                    .addRequestHeader("X-Service", "search-service")
                    .addRequestHeader("X-Gateway", "api-gateway")
                    .circuitBreaker(config -> config
                        .setName("searchServiceCB")
                        .setFallbackUri("forward:/fallback/search")))
                .uri("http://search-service:8084"))
            
            // Health check routes
            .route("user-service-health", r -> r
                .path("/health/user-service")
                .filters(f -> f.rewritePath("/health/user-service", "/actuator/health"))
                .uri("http://user-service:8081"))
            
            .route("hotel-service-health", r -> r
                .path("/health/hotel-service")
                .filters(f -> f.rewritePath("/health/hotel-service", "/actuator/health"))
                .uri("http://hotel-service:8082"))
            
            .route("booking-service-health", r -> r
                .path("/health/booking-service")
                .filters(f -> f.rewritePath("/health/booking-service", "/actuator/health"))
                .uri("http://booking-service:8083"))
            
            .route("search-service-health", r -> r
                .path("/health/search-service")
                .filters(f -> f.rewritePath("/health/search-service", "/actuator/health"))
                .uri("http://search-service:8084"))
            
            .build();
    }
}