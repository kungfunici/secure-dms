package dev.securecdms.service;

import dev.securecdms.dto.request.LoginRequest;
import dev.securecdms.dto.request.RefreshTokenRequest;
import dev.securecdms.dto.request.RegisterRequest;
import dev.securecdms.dto.response.AuthResponse;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.exception.UsernameAlreadyExistsException;
import dev.securecdms.model.Role;
import dev.securecdms.model.User;
import dev.securecdms.repository.UserRepository;
import dev.securecdms.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final AuditService auditService;

    private final Map<String, Long> passwordResetTokens = new ConcurrentHashMap<>();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername()))
            throw new UsernameAlreadyExistsException("Username already taken: " + request.getUsername());
        if (userRepository.existsByEmail(request.getEmail()))
            throw new UsernameAlreadyExistsException("Email already registered: " + request.getEmail());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .build();

        userRepository.save(user);
        log.info("User registered: {}", user.getUsername());

        auditService.log("REGISTER", user.getId(), null,
                "User registered: " + user.getUsername(), null);

        return buildAuthResponse(request.getUsername(), request.getPassword(), user);
    }

    public AuthResponse login(LoginRequest request, String ipAddress) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();

        auditService.log("LOGIN", user.getId(), null,
                "Successful login", ipAddress);

        return buildAuthResponse(user, userDetails);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtils.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String username = jwtUtils.extractUsername(refreshToken);
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("")
                .authorities("ROLE_USER")
                .build();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        String newToken = jwtUtils.generateToken(userDetails);

        return AuthResponse.builder()
                .token(newToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email: " + email));

        String token = UUID.randomUUID().toString();
        passwordResetTokens.put(token, user.getId());

        log.info("Password reset token for {}: {}", email, token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Long userId = passwordResetTokens.remove(token);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password reset for user: {}", user.getUsername());
    }

    private AuthResponse buildAuthResponse(String username, String password, User user) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        return buildAuthResponse(user, (UserDetails) auth.getPrincipal());
    }

    private AuthResponse buildAuthResponse(User user, UserDetails userDetails) {
        String accessToken = jwtUtils.generateToken(userDetails);
        String refreshToken = jwtUtils.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}
