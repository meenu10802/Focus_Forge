package com.focusforge.ai.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("focus.ai")
public record AiProperties(
        @NotBlank String provider,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(20) int maxGoalLength,
        @Valid @NotNull Endpoint groq,
        @Valid @NotNull Endpoint gemini
) {

    /** apiKey may be blank: the provider then reports "not configured" and the fallback kicks in. */
    public record Endpoint(String apiKey, @NotBlank String baseUrl, @NotBlank String model) {

        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
