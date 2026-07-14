package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DocumentResponse {
    private Long id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String description;
    private String documentType;
    private String ownerUsername;
    private String permission;
    private Long folderId;
    private String folderName;
    private Instant deletedAt;
    private Instant uploadedAt;
    private Integer currentVersion;
    private Integer versionCount;
}