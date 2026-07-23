package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LegalHoldResponse {
    private Long id;
    private Long documentId;
    private String documentName;
    private String reason;
    private String createdByUsername;
    private Instant createdAt;
    private Instant releasedAt;
}
