package com.hotel.user.service;

import com.hotel.user.dto.UpdateProfileRequest;
import com.hotel.user.dto.UserResponse;
import com.hotel.user.entity.User;
import com.hotel.user.exception.UserNotFoundException;
import com.hotel.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @InjectMocks
    private UserService userService;
    
    private User testUser;
    private UUID testUserId;
    
    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .fullName("Test User")
                .phone("1234567890")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }
    
    @Test
    void testGetUserById_Success() {
        // Given
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userRepository.findActiveUserById(testUserId)).thenReturn(Optional.of(testUser));
        
        // When
        UserResponse response = userService.getUserById(testUserId);
        
        // Then
        assertNotNull(response);
        assertEquals(testUser.getId(), response.getId());
        assertEquals(testUser.getEmail(), response.getEmail());
        assertEquals(testUser.getFullName(), response.getFullName());
        assertEquals(testUser.getPhone(), response.getPhone());
        assertEquals(testUser.getIsActive(), response.getIsActive());
        
        verify(userRepository).findActiveUserById(testUserId);
        verify(valueOperations).set(anyString(), any(), any(), any());
    }
    
    @Test
    void testGetUserById_FromCache() {
        // Given
        UserResponse cachedResponse = UserResponse.builder()
                .id(testUserId)
                .email("test@example.com")
                .fullName("Test User")
                .build();
        when(valueOperations.get(anyString())).thenReturn(cachedResponse);
        
        // When
        UserResponse response = userService.getUserById(testUserId);
        
        // Then
        assertNotNull(response);
        assertEquals(cachedResponse.getId(), response.getId());
        assertEquals(cachedResponse.getEmail(), response.getEmail());
        
        verify(userRepository, never()).findActiveUserById(any());
    }
    
    @Test
    void testGetUserById_NotFound() {
        // Given
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userRepository.findActiveUserById(testUserId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            userService.getUserById(testUserId);
        });
        
        verify(userRepository).findActiveUserById(testUserId);
    }
    
    @Test
    void testUpdateProfile_Success() {
        // Given
        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name", "9876543210");
        when(userRepository.findActiveUserById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // When
        UserResponse response = userService.updateProfile(testUserId, request);
        
        // Then
        assertNotNull(response);
        assertEquals("Updated Name", testUser.getFullName());
        assertEquals("9876543210", testUser.getPhone());
        
        verify(userRepository).findActiveUserById(testUserId);
        verify(userRepository).save(testUser);
        verify(redisTemplate).delete(anyString());
    }
    
    @Test
    void testUpdateProfile_UserNotFound() {
        // Given
        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name", "9876543210");
        when(userRepository.findActiveUserById(testUserId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            userService.updateProfile(testUserId, request);
        });
        
        verify(userRepository).findActiveUserById(testUserId);
        verify(userRepository, never()).save(any());
    }
    
    @Test
    void testDeleteAccount_Success() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // When
        userService.deleteAccount(testUserId);
        
        // Then
        assertFalse(testUser.getIsActive());
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(testUser);
        verify(redisTemplate).delete(anyString());
    }
    
    @Test
    void testDeleteAccount_UserNotFound() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(UserNotFoundException.class, () -> {
            userService.deleteAccount(testUserId);
        });
        
        verify(userRepository).findById(testUserId);
        verify(userRepository, never()).save(any());
    }
}