package com.hotel.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.user.dto.JwtResponse;
import com.hotel.user.dto.LoginRequest;
import com.hotel.user.dto.RegisterRequest;
import com.hotel.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AuthService authService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @WithMockUser
    void testRegister_Success() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest("test@example.com", "password", "Test User", "1234567890");
        JwtResponse response = JwtResponse.builder()
                .token("jwt-token")
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .build();
        
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);
        
        // When & Then
        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }
    
    @Test
    @WithMockUser
    void testRegister_InvalidInput() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest("", "", "", "");
        
        // When & Then
        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @WithMockUser
    void testLogin_Success() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password");
        JwtResponse response = JwtResponse.builder()
                .token("jwt-token")
                .userId(UUID.randomUUID())
                .email("test@example.com")
                .build();
        
        when(authService.login(any(LoginRequest.class))).thenReturn(response);
        
        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }
    
    @Test
    @WithMockUser
    void testValidateToken_Success() throws Exception {
        // Given
        when(authService.validateToken("valid-token")).thenReturn(true);
        
        // When & Then
        mockMvc.perform(post("/api/v1/auth/validate")
                .with(csrf())
                .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}