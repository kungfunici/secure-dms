package dev.securecdms.service;

import dev.securecdms.dto.request.LoginRequest;
import dev.securecdms.dto.request.RefreshTokenRequest;
import dev.securecdms.dto.request.RegisterRequest;
import dev.securecdms.dto.response.AuthResponse;
import dev.securecdms.exception.EmailAlreadyExistsException;
import dev.securecdms.exception.UsernameAlreadyExistsException;
import dev.securecdms.model.Role;
import dev.securecdms.model.User;
import dev.securecdms.repository.UserRepository;
import dev.securecdms.security.jwt.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtils jwtUtils;
    @Mock private AuditService auditService;
    @Mock private Authentication authentication;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtUtils, auditService);
    }

    @Test
    void register_shouldSucceed() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(
                org.springframework.security.core.userdetails.User.builder()
                        .username("testuser").password("").roles("USER").build());
        when(jwtUtils.generateToken(any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertEquals("testuser", response.getUsername());
        assertEquals("ROLE_USER", response.getRole());
        assertEquals("access-token", response.getToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(userRepository).save(any(User.class));
        verify(auditService).log(eq("REGISTER"), any(), eq(null), any(), eq(null));
    }

    @Test
    void register_shouldThrowWhenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrowWhenEmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("used@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("used@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
    }

    @Test
    void login_shouldSucceed() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        User user = User.builder().id(1L).username("testuser").role(Role.ROLE_USER).build();

        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(
                org.springframework.security.core.userdetails.User.builder()
                        .username("testuser").password("").roles("USER").build());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.login(request, "127.0.0.1");

        assertEquals("testuser", response.getUsername());
        assertEquals("access-token", response.getToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(auditService).log(eq("LOGIN"), eq(1L), eq(null), any(), eq("127.0.0.1"));
    }

    @Test
    void refresh_shouldReturnNewToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        when(jwtUtils.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtils.extractUsername("valid-refresh-token")).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(
                Optional.of(User.builder().id(1L).username("testuser").role(Role.ROLE_USER).build()));
        when(jwtUtils.generateToken(any())).thenReturn("new-access-token");

        AuthResponse response = authService.refresh(request);

        assertEquals("new-access-token", response.getToken());
        assertEquals("valid-refresh-token", response.getRefreshToken());
    }

    @Test
    void refresh_shouldThrowWhenTokenInvalid() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-token");

        when(jwtUtils.validateToken("expired-token")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.refresh(request));
    }

    @Test
    void forgotPassword_shouldStoreToken() {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(User.builder().id(1L).email("test@example.com").build()));

        assertDoesNotThrow(() -> authService.forgotPassword("test@example.com"));
    }

    @Test
    void forgotPassword_shouldThrowWhenEmailNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(dev.securecdms.exception.ResourceNotFoundException.class,
                () -> authService.forgotPassword("unknown@example.com"));
    }
}
