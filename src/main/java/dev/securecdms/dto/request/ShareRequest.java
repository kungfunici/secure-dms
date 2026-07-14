package dev.securecdms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareRequest {
    @NotNull
    private Long userId;

    @NotNull
    private String permissionType;
}
