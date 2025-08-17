package com.hotel.user.service;

import com.hotel.user.dto.JwtResponse;
import com.hotel.user.dto.LoginRequest;
import com.hotel.user.dto.RegisterRequest;
import com.hotel.user.entity.User;
import com.hotel.user.exception.EmailAlreadyExistsException;
import com.hotel.user.repository.UserRepository;
import com.hotel.user.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtUtil jwtUtil;
    
    @InjectMocks
    private AuthService authService;
    
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
    }
    
    @Test
    void testRegister_Success() {
        // Given
        RegisterRequest request = new RegisterRequest("test@example.com", "password", "Test User", "1234567890");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtil.generateToken(anyString(), any(UUID.class))).thenReturn("jwt-token");
        
        // When
        JwtResponse response = authService.register(request);
        
        // Then
        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals(testUser.getId(), response.getUserId());
        assertEquals(testUser.getEmail(), response.getEmail());
        assertEquals("Bearer", response.getType());
        
        verify(userRepository).existsByEmail(request.getEmail());
        verify(passwordEncoder).encode(request.getPassword());
        verify(userRepository).save(any(User.class));
        verify(jwtUtil).generateToken(testUser.getEmail(), testUser.getId());
    }
    
    @Test
    void testRegister_EmailAlreadyExists() {
        // Given
        RegisterRequest request = new RegisterRequest("test@example.com", "password", "Test User", "1234567890");
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        // When & Then
        assertThrows(EmailAlreadyExistsException.class, () -> {
            authService.register(request);
        });
        
        verify(userRepository).existsByEmail(request.getEmail());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }
    
    @Test
    void testLogin_Success() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getPassword(), testUser.getPasswordHash())).thenReturn(true);
        when(jwtUtil.generateToken(testUser.getEmail(), testUser.getId())).thenReturn("jwt-token");
        
        // When
        JwtResponse response = authService.login(request);
        
        // Then
        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals(testUser.getId(), response.getUserId());
        assertEquals(testUser.getEmail(), response.getEmail());
        
        verify(userRepository).findByEmail(request.getEmail());
        verify(passwordEncoder).matches(request.getPassword(), testUser.getPasswordHash());
        verify(jwtUtil).generateToken(testUser.getEmail(), testUser.getId());
    }
    
    @Test
    void testLogin_UserNotFound() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(UsernameNotFoundException.class, () -> {
            authService.login(request);
        });
        
        verify(userRepository).findByEmail(request.getEmail());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
    
    @Test
    void testLogin_InvalidPassword() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getPassword(), testUser.getPasswordHash())).thenReturn(false);
        
        // When & Then
        assertThrows(BadCredentialsException.class, () -> {
            authService.login(request);
        });
        
        verify(userRepository).findByEmail(request.getEmail());
        verify(passwordEncoder).matches(request.getPassword(), testUser.getPasswordHash());
        verify(jwtUtil, never()).generateToken(anyString(), any());
    }
    
    @Test
    void testLogin_InactiveUser() {
        // Given
        testUser.setIsActive(false);
        LoginRequest request = new LoginRequest("test@example.com", "password");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        
        // When & Then
        assertThrows(BadCredentialsException.class, () -> {
            authService.login(request);
        });
        
        verify(userRepository).findByEmail(request.getEmail());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
    
    @Test
    void testValidateToken() {
        // Given
        String token = "valid-token";
        when(jwtUtil.validateToken(token)).thenReturn(true);
        
        // When
        boolean result = authService.validateToken(token);
        
        // Then
        assertTrue(result);
        verify(jwtUtil).validateToken(token);
    }
    
    @Test
    void testExtractEmailFromToken() {
        // Given
        String token = "valid-token";
        String email = "test@example.com";
        when(jwtUtil.extractEmail(token)).thenReturn(email);
        
        // When
        String result = authService.extractEmailFromToken(token);
        
        // Then
        assertEquals(email, result);
        verify(jwtUtil).extractEmail(token);
    }
}