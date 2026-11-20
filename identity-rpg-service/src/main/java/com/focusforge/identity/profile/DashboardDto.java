package com.focusforge.identity.profile;

import java.util.UUID;

/**
 * Everything the HUD needs in one call. Cached in Redis per (user, day).
 * Coins are NOT here — bank-store-service owns the wallet.
 */
public record DashboardDto(
        UUID userId,
        String displayName,
        String role,
        int level,
        int hp,
        int maxHp,
        long xp,
        long xpForNextLevel,
        int streakDays
) {
}
