package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

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
    private int currentVersion;
    private int versionCount;
    private boolean favorite;
    private List<String> tags;
    private Instant retentionAt;
    private boolean legalHold;
}