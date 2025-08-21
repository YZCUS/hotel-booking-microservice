package com.hotel.user.controller;

import com.hotel.user.dto.UpdateProfileRequest;
import com.hotel.user.dto.UserResponse;
import com.hotel.user.exception.AccessDeniedException;
import com.hotel.user.service.UserService;
import com.hotel.user.util.InternalServiceUtil;
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
// CORS configuration moved to global configuration for security
public class UserController {
    
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final InternalServiceUtil internalServiceUtil;
    
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
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID userId, 
            HttpServletRequest request) {
        log.info("Getting user by id: {}", userId);
        
        // Check if this is an authenticated internal service call
        if (isInternalServiceCall(request)) {
            log.info("Internal service access to user: {}", userId);
            UserResponse response = userService.getUserById(userId);
            return ResponseEntity.ok(response);
        }
        
        // For external calls, check authorization
        UUID currentUserId = extractUserIdFromRequest(request);
        String userRole = extractUserRoleFromRequest(request);
        
        // Authorization check: users can only access their own data, admins can access all
        if (!currentUserId.equals(userId) && !"ADMIN".equals(userRole)) {
            log.warn("Access denied: User {} attempted to access user {} data", currentUserId, userId);
            throw new AccessDeniedException("You can only access your own profile");
        }
        
        log.info("Authorized access to user: {} by user: {} (role: {})", userId, currentUserId, userRole);
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
    
    private String extractUserRoleFromRequest(HttpServletRequest request) {
        try {
            // Try to get role from header first (set by API Gateway)
            String role = request.getHeader("X-User-Role");
            if (role != null && !role.trim().isEmpty()) {
                return role.trim();
            }
            
            // Fallback to default role if not found
            log.debug("No role found in headers, defaulting to USER");
            return "USER";
        } catch (Exception e) {
            log.warn("Failed to extract role from request", e);
            return "USER"; // Default role
        }
    }
    
    private boolean isInternalServiceCall(HttpServletRequest request) {
        // Check if Spring Security has already authenticated this as an internal service
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_INTERNAL_SERVICE".equals(authority.getAuthority()));
        }
        return false;
    }
}