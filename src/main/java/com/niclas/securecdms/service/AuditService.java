package com.niclas.securecdms.service;

import com.niclas.securecdms.model.AuditLog;
import com.niclas.securecdms.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // Async damit Audit-Writes den eigentlichen Request nicht verlangsamen
    @Async
    public void log(AuditLog entry) {
        auditLogRepository.save(entry);
    }

    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<AuditLog> getLogsByDocument(Long documentId, Pageable pageable) {
        return auditLogRepository.findByDocumentIdOrderByTimestampDesc(documentId, pageable);
    }
}
