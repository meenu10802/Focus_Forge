package com.focusforge.events;

import java.time.Instant;
import java.util.UUID;

/** Published by quest-engine when a micro-task is completed. Topic: {@link Topics#TASK_COMPLETED}. */
public record TaskCompletedEvent(
        UUID eventId,
        UUID userId,
        Instant occurredAt,
        UUID taskId,
        UUID questId,
        int coinsEarned,
        int xpEarned,
        int hpRestored,
        int focusMinutes
) implements FocusEvent {

    public static TaskCompletedEvent of(UUID userId, UUID taskId, UUID questId,
                                        int coins, int xp, int hp, int minutes) {
        return new TaskCompletedEvent(UUID.randomUUID(), userId, Instant.now(),
                taskId, questId, coins, xp, hp, minutes);
    }
}
