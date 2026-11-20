package com.focusforge.identity.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Validated
@ConfigurationProperties("focus.rpg")
public record RpgProperties(
        @Min(1) int maxHp,
        @NotNull ZoneId timezone,
        @NotNull Duration dashboardCacheTtl,
        List<String> adminEmails
) {

    public RpgProperties {
        adminEmails = adminEmails == null ? List.of()
                : adminEmails.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .toList();
    }

    public boolean isAdminEmail(String normalizedEmail) {
        return adminEmails.contains(normalizedEmail);
    }
}
