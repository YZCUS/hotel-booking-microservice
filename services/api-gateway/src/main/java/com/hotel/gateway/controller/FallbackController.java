package com.hotel.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {
    
    @GetMapping("/hotels")
    public ResponseEntity<Map<String, Object>> hotelServiceFallback() {
        log.warn("Hotel service fallback triggered");
        
        Map<String, Object> response = Map.of(
            "message", "Hotel service is temporarily unavailable. Please try again later.",
            "service", "hotel-service",
            "timestamp", Instant.now().toString(),
            "status", "service_unavailable"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    @GetMapping("/bookings")
    public ResponseEntity<Map<String, Object>> bookingServiceFallback() {
        log.warn("Booking service fallback triggered");
        
        Map<String, Object> response = Map.of(
            "message", "Booking service is temporarily unavailable. Please try again later.",
            "service", "booking-service",
            "timestamp", Instant.now().toString(),
            "status", "service_unavailable"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchServiceFallback() {
        log.warn("Search service fallback triggered");
        
        Map<String, Object> response = Map.of(
            "message", "Search service is temporarily unavailable. Please try again later.",
            "service", "search-service",
            "timestamp", Instant.now().toString(),
            "status", "service_unavailable",
            "suggestion", "Try browsing hotels directly or check back in a few minutes"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> userServiceFallback() {
        log.warn("User service fallback triggered");
        
        Map<String, Object> response = Map.of(
            "message", "User service is temporarily unavailable. Please try again later.",
            "service", "user-service",
            "timestamp", Instant.now().toString(),
            "status", "service_unavailable"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}