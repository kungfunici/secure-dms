package com.niclas.securecdms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Nur alphanumerische Zeichen, _ und - erlaubt")
    private String username;

    @NotBlank
    @Email(message = "Ungültige E-Mail-Adresse")
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100, message = "Passwort muss mindestens 8 Zeichen lang sein")
    private String password;
}
