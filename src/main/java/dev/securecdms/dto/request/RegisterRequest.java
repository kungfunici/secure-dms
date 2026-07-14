package dev.securecdms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Only alphanumeric characters, _ and - allowed")
    private String username;

    @NotBlank
    @Email(message = "Invalid email address")
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long")
    private String password;
}
