package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FolderResponse {
    private Long id;
    private String name;
    private long documentCount;
    private Instant createdAt;
}
