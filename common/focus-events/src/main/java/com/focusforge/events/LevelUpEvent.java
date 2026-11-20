package com.focusforge.events;

import java.time.Instant;
import java.util.UUID;

/** Published by identity-rpg-service when XP crosses a level threshold. Topic: {@link Topics#LEVEL_UP}. */
public record LevelUpEvent(
        UUID eventId,
        UUID userId,
        Instant occurredAt,
        int previousLevel,
        int newLevel
) implements FocusEvent {

    public static LevelUpEvent of(UUID userId, int previousLevel, int newLevel) {
        return new LevelUpEvent(UUID.randomUUID(), userId, Instant.now(), previousLevel, newLevel);
    }
}
