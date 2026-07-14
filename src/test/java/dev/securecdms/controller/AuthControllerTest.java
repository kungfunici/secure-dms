package dev.securecdms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.securecdms.dto.request.ForgotPasswordRequest;
import dev.securecdms.dto.request.LoginRequest;
import dev.securecdms.dto.request.RefreshTokenRequest;
import dev.securecdms.dto.request.RegisterRequest;
import dev.securecdms.dto.request.ResetPasswordRequest;
import dev.securecdms.dto.response.AuthResponse;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.exception.UsernameAlreadyExistsException;
import dev.securecdms.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;

    @Test
    void register_shouldReturnToken() throws Exception {
        when(authService.register(any())).thenReturn(
                AuthResponse.builder()
                        .token("jwt-token").refreshToken("refresh-token").tokenType("Bearer")
                        .username("testuser").role("ROLE_USER").build());

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void register_shouldReturn409WhenUsernameExists() throws Exception {
        when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException("Username already taken"));

        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_shouldReturn400WhenValidationFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab");
        request.setEmail("invalid");
        request.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_shouldReturnToken() throws Exception {
        when(authService.login(any(), anyString())).thenReturn(
                AuthResponse.builder().token("jwt-token").tokenType("Bearer").username("testuser").role("ROLE_USER").build());

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void refresh_shouldReturnNewToken() throws Exception {
        when(authService.refresh(any())).thenReturn(
                AuthResponse.builder().token("new-token").refreshToken("old-refresh").tokenType("Bearer")
                        .username("testuser").role("ROLE_USER").build());

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-token"));
    }

    @Test
    void forgotPassword_shouldReturnOk() throws Exception {
        doNothing().when(authService).forgotPassword(anyString());

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the email exists, a reset token has been generated"));
    }

    @Test
    void forgotPassword_shouldReturn404WhenEmailNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("No user found with email: unknown@example.com"))
                .when(authService).forgotPassword("unknown@example.com");

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("unknown@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetPassword_shouldReturnOk() throws Exception {
        doNothing().when(authService).resetPassword(anyString(), anyString());

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("newpass123");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully"));
    }
}
