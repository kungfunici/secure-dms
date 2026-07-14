package dev.securecdms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username darf nicht leer sein")
    private String username;

    @NotBlank(message = "Password darf nicht leer sein")
    private String password;
}
