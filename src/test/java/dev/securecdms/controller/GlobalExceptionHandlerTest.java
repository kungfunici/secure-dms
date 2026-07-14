package dev.securecdms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.securecdms.dto.request.RegisterRequest;
import dev.securecdms.exception.AccessDeniedException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;

    @Test
    void badCredentials_shouldReturn401() throws Exception {
        when(authService.login(any(), any())).thenThrow(new BadCredentialsException("bad creds"));

        String body = """
                {"username":"user","password":"wrong"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void resourceNotFound_shouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("Not found")).when(authService).forgotPassword(anyString());

        String body = """
                {"email":"unknown@example.com"}
                """;

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void usernameAlreadyExists_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user");
        request.setEmail("a@b.com");
        request.setPassword("password123");

        when(authService.register(any())).thenThrow(new UsernameAlreadyExistsException("Username already taken"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void accessDenied_shouldReturn403() throws Exception {
        when(authService.refresh(any())).thenThrow(new AccessDeniedException("Access denied"));

        String body = """
                {"refreshToken":"some-token"}
                """;

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void illegalArgument_shouldReturn400() throws Exception {
        when(authService.refresh(any())).thenThrow(new IllegalArgumentException("Invalid token"));

        String body = """
                {"refreshToken":"bad"}
                """;

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid token"));
    }

    @Test
    void validationError_shouldReturn400() throws Exception {
        String body = """
                {"username":"","email":"invalid","password":"short"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }
}
