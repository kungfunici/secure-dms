package dev.securecdms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    private Long id;
    private String token;
    private String refreshToken;
    private String tokenType;
    private String username;
    private String role;
}
