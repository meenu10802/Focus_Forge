package com.focusforge.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope for every event on the FocusForge bus.
 * <p>
 * {@code eventId} is the idempotency handle for consumers (stored in each
 * service's {@code processed_events} table). {@code userId} is always used as
 * the Kafka message key so events for one player stay ordered on a partition.
 */
public sealed interface FocusEvent
        permits TaskCompletedEvent, TaskAbandonedEvent, LevelUpEvent,
                RewardRedeemedEvent, StreakMilestoneEvent {

    UUID eventId();

    UUID userId();

    Instant occurredAt();
}
