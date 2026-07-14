package dev.securecdms.service;

import dev.securecdms.model.AuditLog;
import dev.securecdms.model.User;
import dev.securecdms.repository.AuditLogRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    // Nimmt userId als Long statt User-Entity — kein JPA-Session-Problem im Async-Thread
    @Async
    public void log(String action, Long userId, Long documentId, String details, String ipAddress) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        AuditLog entry = AuditLog.builder()
                .action(action)
                .user(user)
                .documentId(documentId)
                .details(details)
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(entry);
    }

    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<AuditLog> getLogsByDocument(Long documentId, Pageable pageable) {
        return auditLogRepository.findByDocumentIdOrderByTimestampDesc(documentId, pageable);
    }
}
