package com.focusforge.events;

import java.time.Instant;
import java.util.UUID;

/** Published by quest-engine when a focus session is abandoned. Topic: {@link Topics#TASK_ABANDONED}. */
public record TaskAbandonedEvent(
        UUID eventId,
        UUID userId,
        Instant occurredAt,
        UUID taskId,
        int hpLost
) implements FocusEvent {

    public static TaskAbandonedEvent of(UUID userId, UUID taskId, int hpLost) {
        return new TaskAbandonedEvent(UUID.randomUUID(), userId, Instant.now(), taskId, hpLost);
    }
}
