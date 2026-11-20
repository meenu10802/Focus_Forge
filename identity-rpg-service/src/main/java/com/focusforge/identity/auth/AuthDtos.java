package com.focusforge.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            @NotBlank @Size(min = 2, max = 60) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record UserSummary(UUID id, String email, String displayName, String role) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
    }
}
