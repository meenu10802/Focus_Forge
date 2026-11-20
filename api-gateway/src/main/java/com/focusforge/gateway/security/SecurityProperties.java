package com.focusforge.gateway.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

public final class SecurityProperties {

    private SecurityProperties() {
    }

    @Validated
    @ConfigurationProperties("focus.jwt")
    public record Jwt(
            @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
            @NotBlank String issuer
    ) {
    }

    @ConfigurationProperties("focus.security")
    public record Paths(List<String> publicPaths, Map<String, String> rolePaths) {

        public Paths {
            publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
            rolePaths = rolePaths == null ? Map.of() : Map.copyOf(rolePaths);
        }
    }
}
