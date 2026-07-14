package com.niclas.securecdms.repository;

import com.niclas.securecdms.model.AuditLog;
import com.niclas.securecdms.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUserOrderByTimestampDesc(User user, Pageable pageable);
    Page<AuditLog> findByDocumentIdOrderByTimestampDesc(Long documentId, Pageable pageable);
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
