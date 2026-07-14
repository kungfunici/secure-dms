package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class VersionResponse {
    private Long id;
    private Integer versionNumber;
    private Long fileSize;
    private String contentType;
    private String uploadedByUsername;
    private Instant createdAt;
}
