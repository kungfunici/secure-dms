package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String profilePicture;
    private String role;
    private boolean enabled;
    private int versionRetentionDays;
    private Instant createdAt;
}
