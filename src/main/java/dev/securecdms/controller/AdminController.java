package dev.securecdms.controller;

import dev.securecdms.model.AuditLog;
import dev.securecdms.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuditService auditService;

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(Pageable pageable) {
        return ResponseEntity.ok(
                auditService.getAllLogs(pageable).map(AuditLogResponse::from));
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
