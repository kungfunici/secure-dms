package dev.securecdms.dto.request;

import lombok.Data;

@Data
public class LegalHoldRequest {
    private Long documentId;
    private String reason;
}
