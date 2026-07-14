package com.niclas.securecdms.service;

import com.niclas.securecdms.dto.request.LoginRequest;
import com.niclas.securecdms.dto.request.RegisterRequest;
import com.niclas.securecdms.dto.response.AuthResponse;
import com.niclas.securecdms.exception.UsernameAlreadyExistsException;
import com.niclas.securecdms.model.AuditLog;
import com.niclas.securecdms.model.Role;
import com.niclas.securecdms.model.User;
import com.niclas.securecdms.repository.UserRepository;
import com.niclas.securecdms.security.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final AuditService auditService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException("Username bereits vergeben: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UsernameAlreadyExistsException("E-Mail bereits registriert: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .build();

        userRepository.save(user);
        log.info("Neuer User registriert: {}", user.getUsername());

        auditService.log(AuditLog.builder()
                .action("REGISTER")
                .user(user)
                .details("User registriert: " + user.getUsername())
                .build());

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String token = jwtUtils.generateToken((UserDetails) auth.getPrincipal());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request, String ipAddress) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        String token = jwtUtils.generateToken(userDetails);

        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();

        auditService.log(AuditLog.builder()
                .action("LOGIN")
                .user(user)
                .ipAddress(ipAddress)
                .details("Erfolgreich eingeloggt")
                .build());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}
