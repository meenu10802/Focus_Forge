package com.focusforge.events;

import java.time.Instant;
import java.util.UUID;

/** Published by identity-rpg-service when a daily streak hits a milestone. Topic: {@link Topics#STREAK_MILESTONE}. */
public record StreakMilestoneEvent(
        UUID eventId,
        UUID userId,
        Instant occurredAt,
        int streakDays
) implements FocusEvent {

    public static StreakMilestoneEvent of(UUID userId, int streakDays) {
        return new StreakMilestoneEvent(UUID.randomUUID(), userId, Instant.now(), streakDays);
    }
}
