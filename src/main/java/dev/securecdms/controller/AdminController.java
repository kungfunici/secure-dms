package dev.securecdms.controller;

import dev.securecdms.dto.request.LegalHoldRequest;
import dev.securecdms.dto.request.RetentionPolicyRequest;
import dev.securecdms.dto.response.LegalHoldResponse;
import dev.securecdms.dto.response.RetentionPolicyResponse;
import dev.securecdms.dto.response.UserResponse;
import dev.securecdms.model.AuditLog;
import dev.securecdms.model.User;
import dev.securecdms.service.AdminService;
import dev.securecdms.service.RetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final RetentionService retentionService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> getUsers(Pageable pageable) {
        return ResponseEntity.ok(
                adminService.getUsers(pageable).map(this::toUserResponse));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                toUserResponse(adminService.updateUserRole(id, role)));
    }

    @PatchMapping("/users/{id}/enabled")
    public ResponseEntity<UserResponse> setUserEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        return ResponseEntity.ok(
                toUserResponse(adminService.setUserEnabled(id, enabled)));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            Pageable pageable) {
        return ResponseEntity.ok(
                adminService.getAuditLogs(action, username, dateFrom, dateTo, pageable)
                        .map(AuditLogResponse::from));
    }

    // ---- Retention Policies ----

    @GetMapping("/retention-policies")
    public ResponseEntity<Page<RetentionPolicyResponse>> getRetentionPolicies(Pageable pageable) {
        return ResponseEntity.ok(retentionService.getPolicies(pageable));
    }

    @PostMapping("/retention-policies")
    public ResponseEntity<RetentionPolicyResponse> createRetentionPolicy(@RequestBody RetentionPolicyRequest req) {
        return ResponseEntity.ok(retentionService.createPolicy(req));
    }

    @PutMapping("/retention-policies/{id}")
    public ResponseEntity<RetentionPolicyResponse> updateRetentionPolicy(
            @PathVariable Long id, @RequestBody RetentionPolicyRequest req) {
        return ResponseEntity.ok(retentionService.updatePolicy(id, req));
    }

    @DeleteMapping("/retention-policies/{id}")
    public ResponseEntity<Void> deleteRetentionPolicy(@PathVariable Long id) {
        retentionService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Legal Holds ----

    @GetMapping("/legal-holds")
    public ResponseEntity<Page<LegalHoldResponse>> getLegalHolds(Pageable pageable) {
        return ResponseEntity.ok(retentionService.getLegalHolds(pageable));
    }

    @PostMapping("/legal-holds")
    public ResponseEntity<LegalHoldResponse> createLegalHold(
            @RequestBody LegalHoldRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(retentionService.createLegalHold(req, userDetails.getUsername()));
    }

    @PostMapping("/legal-holds/{id}/release")
    public ResponseEntity<LegalHoldResponse> releaseLegalHold(@PathVariable Long id) {
        return ResponseEntity.ok(retentionService.releaseLegalHold(id));
    }

    // ---- System Config ----

    @GetMapping("/system/config")
    public ResponseEntity<Map<String, String>> getSystemConfig() {
        return ResponseEntity.ok(adminService.getSystemConfig());
    }

    @PutMapping("/system/config")
    public ResponseEntity<Map<String, String>> updateSystemConfig(@RequestBody Map<String, String> config) {
        return ResponseEntity.ok(adminService.updateSystemConfig(config));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .profilePicture(user.getProfilePicture())
                .role(user.getRole().name().replace("ROLE_", ""))
                .enabled(user.isEnabled())
                .versionRetentionDays(user.getVersionRetentionDays())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public record AuditLogResponse(
            Long id,
            String action,
            String username,
            Long documentId,
            String details,
            String ipAddress,
            Instant timestamp) {

        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getAction(),
                    log.getUser() != null ? log.getUser().getUsername() : null,
                    log.getDocumentId(),
                    log.getDetails(),
                    log.getIpAddress(),
                    log.getTimestamp());
        }
    }
}
