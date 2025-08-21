package com.hotel.hotel.controller;

import com.hotel.hotel.dto.HotelResponse;
import com.hotel.hotel.service.FavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@Slf4j
// CORS configuration moved to global configuration for security
public class FavoriteController {
    
    private final FavoriteService favoriteService;
    
    @PostMapping("/{hotelId}")
    public ResponseEntity<Void> addFavorite(
            @PathVariable UUID hotelId,
            HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        log.info("Adding hotel {} to favorites for user {}", hotelId, userId);
        
        favoriteService.addFavorite(userId, hotelId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    
    @GetMapping
    public ResponseEntity<List<HotelResponse>> getUserFavorites(HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        log.info("Getting favorites for user: {}", userId);
        
        List<HotelResponse> favorites = favoriteService.getUserFavorites(userId);
        return ResponseEntity.ok(favorites);
    }
    
    @DeleteMapping("/{hotelId}")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable UUID hotelId,
            HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        log.info("Removing hotel {} from favorites for user {}", hotelId, userId);
        
        favoriteService.removeFavorite(userId, hotelId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{hotelId}/check")
    public ResponseEntity<Boolean> checkFavorite(
            @PathVariable UUID hotelId,
            HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        
        boolean isFavorite = favoriteService.isFavorite(userId, hotelId);
        return ResponseEntity.ok(isFavorite);
    }
    
    @GetMapping("/count")
    public ResponseEntity<Long> getUserFavoriteCount(HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        
        Long count = favoriteService.getUserFavoriteCount(userId);
        return ResponseEntity.ok(count);
    }
    
    @GetMapping("/{hotelId}/count")
    public ResponseEntity<Long> getHotelFavoriteCount(@PathVariable UUID hotelId) {
        Long count = favoriteService.getFavoriteCount(hotelId);
        return ResponseEntity.ok(count);
    }
    
    @DeleteMapping
    public ResponseEntity<Void> removeAllFavorites(HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        log.info("Removing all favorites for user: {}", userId);
        
        favoriteService.removeAllUserFavorites(userId);
        return ResponseEntity.noContent().build();
    }
    
    private UUID extractUserIdFromRequest(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid User ID format");
        }
    }
}