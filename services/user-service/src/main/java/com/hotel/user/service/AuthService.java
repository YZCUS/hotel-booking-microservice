package com.hotel.user.service;

import com.hotel.user.dto.JwtResponse;
import com.hotel.user.dto.LoginRequest;
import com.hotel.user.dto.RegisterRequest;
import com.hotel.user.entity.User;
import com.hotel.user.exception.EmailAlreadyExistsException;
import com.hotel.user.repository.UserRepository;
import com.hotel.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    
    public JwtResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());
        
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.getEmail());
        }
        
        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .isActive(true)
                .build();
        
        User saved = userRepository.save(user);
        
        // Generate JWT token
        String token = jwtUtil.generateToken(saved.getEmail(), saved.getId());
        
        log.info("User registered successfully: {}", saved.getEmail());
        
        return JwtResponse.builder()
                .token(token)
                .userId(saved.getId())
                .email(saved.getEmail())
                .build();
    }
    
    public JwtResponse login(LoginRequest request) {
        log.info("User login attempt: {}", request.getEmail());
        
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        
        // Check if account is active
        if (!user.getIsActive()) {
            throw new BadCredentialsException("Account is disabled");
        }
        
        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        
        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        
        log.info("User logged in successfully: {}", user.getEmail());
        
        return JwtResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }
    
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }
    
    public String extractEmailFromToken(String token) {
        return jwtUtil.extractEmail(token);
    }
}