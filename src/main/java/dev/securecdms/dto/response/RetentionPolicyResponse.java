package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RetentionPolicyResponse {
    private Long id;
    private String name;
    private String documentType;
    private Long folderId;
    private String folderName;
    private Integer retentionDays;
    private String action;
    private Boolean enabled;
    private Instant createdAt;
}
