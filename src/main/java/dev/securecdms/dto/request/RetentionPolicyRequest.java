package dev.securecdms.dto.request;

import lombok.Data;

@Data
public class RetentionPolicyRequest {
    private String name;
    private String documentType;
    private Long folderId;
    private Integer retentionDays;
    private String action;
    private Boolean enabled;
}
