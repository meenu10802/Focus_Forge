package com.focusforge.quest.focus;

import java.time.Instant;
import java.util.UUID;

public final class FocusDtos {

    private FocusDtos() {
    }

    public record SessionDto(UUID sessionId, UUID taskId, UUID questId, String taskTitle,
                             int plannedMinutes, Instant startedAt, FocusSession.Status status) {

        static SessionDto of(FocusSession s) {
            return new SessionDto(s.getId(), s.getTask().getId(), s.getTask().getQuest().getId(),
                    s.getTask().getTitle(), s.getPlannedMinutes(), s.getStartedAt(), s.getStatus());
        }
    }

    /** Body of a successful completion. Replayed verbatim for a repeated Idempotency-Key. */
    public record CompletionResult(UUID taskId, UUID questId, int coinsEarned, int xpEarned, int hpRestored,
                                   int focusMinutes, boolean questCompleted, UUID eventId) {
    }

    public record AbandonResult(UUID taskId, int hpLost, UUID eventId) {
    }
}
