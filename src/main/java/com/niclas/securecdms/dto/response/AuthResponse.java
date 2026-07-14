package com.niclas.securecdms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tokenType;
    private String username;
    private String role;
}
