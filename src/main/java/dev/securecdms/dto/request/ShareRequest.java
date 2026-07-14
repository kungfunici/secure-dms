package dev.securecdms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareRequest {
    @NotBlank
    private String username;

    @NotNull
    private String permissionType;
}
