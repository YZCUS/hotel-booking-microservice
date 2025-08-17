package com.hotel.user.controller;

import com.hotel.user.dto.UpdateProfileRequest;
import com.hotel.user.dto.UserResponse;
import com.hotel.user.service.UserService;
import com.hotel.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {
    
    private final UserService userService;
    private final JwtUtil jwtUtil;
    
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        log.info("Getting profile for user: {}", userId);
        
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            HttpServletRequest request,
            @Valid @RequestBody UpdateProfileRequest updateRequest) {
        UUID userId = extractUserIdFromRequest(request);
        log.info("Updating profile for user: {}", userId);
        
        UserResponse response = userService.updateProfile(userId, updateRequest);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(HttpServletRequest request) {
        UUID userId = extractUserIdFromRequest(request);
        log.info("Deleting account for user: {}", userId);
        
        userService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        log.info("Getting user by id: {}", userId);
        
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }
    
    private UUID extractUserIdFromRequest(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        return jwtUtil.extractUserId(token);
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new RuntimeException("JWT Token is missing");
    }
}