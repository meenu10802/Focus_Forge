package com.focusforge.identity.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** HS256 needs a key of at least 256 bits, hence the 32-char minimum. */
@Validated
@ConfigurationProperties("focus.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
        @NotBlank String issuer,
        @NotNull Duration ttl
) {
}
