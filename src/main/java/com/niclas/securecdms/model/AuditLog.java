package com.niclas.securecdms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_document", columnList = "document_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String action;  // z.B. UPLOAD, DOWNLOAD, DELETE, LOGIN, PERMISSION_GRANT

    // Nullable: System-Events haben keinen User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Nullable: nicht alle Events betreffen ein Dokument
    @Column(name = "document_id")
    private Long documentId;

    @Column(length = 500)
    private String details;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
