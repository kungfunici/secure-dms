package dev.securecdms.repository;

import dev.securecdms.model.AuditLog;
import dev.securecdms.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUserOrderByTimestampDesc(User user, Pageable pageable);
    Page<AuditLog> findByDocumentIdOrderByTimestampDesc(Long documentId, Pageable pageable);
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
    long countByTimestampAfter(Instant after);
    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);
    Page<AuditLog> findByUserUsernameContainingIgnoreCaseOrderByTimestampDesc(String username, Pageable pageable);
    Page<AuditLog> findByActionAndUserUsernameContainingIgnoreCaseOrderByTimestampDesc(String action, String username, Pageable pageable);
}
