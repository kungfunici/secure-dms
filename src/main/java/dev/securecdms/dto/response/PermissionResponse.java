package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PermissionResponse {
    private Long id;
    private Long userId;
    private String username;
    private String permissionType;
    private Instant grantedAt;
}
