package dev.securecdms.service;

import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.AuditLog;
import dev.securecdms.model.Role;
import dev.securecdms.model.SystemConfig;
import dev.securecdms.model.User;
import dev.securecdms.repository.AuditLogRepository;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.SystemConfigRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;
    private final SystemConfigRepository systemConfigRepository;

    public Map<String, Object> getStats() {
        long userCount = userRepository.count();
        long documentCount = documentRepository.count();
        long auditLogCount24h = auditLogRepository.countByTimestampAfter(Instant.now().minusSeconds(86400));

        return Map.of(
                "userCount", userCount,
                "documentCount", documentCount,
                "auditLogCount24h", auditLogCount24h
        );
    }

    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User updateUserRole(Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Role newRole;
        try {
            newRole = Role.valueOf("ROLE_" + role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }

        user.setRole(newRole);
        User saved = userRepository.save(user);
        log.info("Admin updated role for user {}: {}", userId, newRole);
        return saved;
    }

    @Transactional
    public User setUserEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setEnabled(enabled);
        User saved = userRepository.save(user);
        log.info("Admin {} user {}: {}",
                enabled ? "enabled" : "disabled", userId, saved.getUsername());
        return saved;
    }

    public Page<AuditLog> getAuditLogs(String action, String username,
                                        Instant dateFrom, Instant dateTo,
                                        Pageable pageable) {
        if (action != null && !action.isBlank() && username != null && !username.isBlank()) {
            return auditLogRepository.findByActionAndUserUsernameContainingIgnoreCaseOrderByTimestampDesc(
                    action, username, pageable);
        }
        if (action != null && !action.isBlank()) {
            return auditLogRepository.findByActionOrderByTimestampDesc(action, pageable);
        }
        if (username != null && !username.isBlank()) {
            return auditLogRepository.findByUserUsernameContainingIgnoreCaseOrderByTimestampDesc(username, pageable);
        }
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getSystemConfig() {
        return systemConfigRepository.findAll().stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : "",
                        (a, b) -> b,
                        LinkedHashMap::new));
    }

    @Transactional
    public Map<String, String> updateSystemConfig(Map<String, String> config) {
        for (var entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            systemConfigRepository.findByConfigKey(key).ifPresentOrElse(
                    c -> {
                        c.setConfigValue(value);
                        systemConfigRepository.save(c);
                    },
                    () -> {
                        SystemConfig c = SystemConfig.builder()
                                .configKey(key)
                                .configValue(value)
                                .build();
                        systemConfigRepository.save(c);
                    });
        }
        return getSystemConfig();
    }
}
