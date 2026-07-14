package dev.securecdms.dto.request;

import dev.securecdms.model.DocumentPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PermissionRequest {

    @NotBlank
    private String username;

    @NotNull
    private DocumentPermission.PermissionType permissionType;
}
