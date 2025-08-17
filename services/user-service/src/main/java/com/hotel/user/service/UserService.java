package com.hotel.user.service;

import com.hotel.user.dto.UpdateProfileRequest;
import com.hotel.user.dto.UserResponse;
import com.hotel.user.entity.User;
import com.hotel.user.exception.UserNotFoundException;
import com.hotel.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String USER_CACHE_PREFIX = "user:";
    private static final int CACHE_TTL_MINUTES = 30;
    
    public UserResponse getUserById(UUID userId) {
        log.info("Getting user by id: {}", userId);
        
        // Check cache first
        String cacheKey = USER_CACHE_PREFIX + userId;
        UserResponse cached = (UserResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("User found in cache: {}", userId);
            return cached;
        }
        
        // Get from database
        User user = userRepository.findActiveUserById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        
        UserResponse response = mapToResponse(user);
        
        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        
        return response;
    }
    
    public UserResponse getUserByEmail(String email) {
        log.info("Getting user by email: {}", email);
        
        User user = userRepository.findActiveUserByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        
        return mapToResponse(user);
    }
    
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        log.info("Updating profile for user: {}", userId);
        
        User user = userRepository.findActiveUserById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        
        User updated = userRepository.save(user);
        
        // Update cache
        String cacheKey = USER_CACHE_PREFIX + userId;
        redisTemplate.delete(cacheKey);
        
        UserResponse response = mapToResponse(updated);
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        
        return response;
    }
    
    public void deleteAccount(UUID userId) {
        log.info("Soft deleting user account: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        user.setIsActive(false);
        userRepository.save(user);
        
        // Clear cache
        String cacheKey = USER_CACHE_PREFIX + userId;
        redisTemplate.delete(cacheKey);
    }
    
    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}