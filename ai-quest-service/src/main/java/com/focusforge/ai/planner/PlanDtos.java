package com.focusforge.ai.planner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Wire contract of the internal API consumed by quest-engine-service.
 * Rewards (coins/XP/HP) are deliberately absent: the economy is owned by
 * quest-engine and derived from {@code difficulty}, never from model output.
 */
public final class PlanDtos {

    private PlanDtos() {
    }

    public enum Difficulty { MICRO, EASY, MEDIUM, HARD }

    /** AI_GENERATED = from the model; FALLBACK = built-in template (AI unavailable). */
    public enum Source { AI_GENERATED, FALLBACK }

    public record PlannedTask(String title, String description, int minutes, Difficulty difficulty) {
    }

    public record PlanResponse(String questTitle, Source source, String provider, List<PlannedTask> tasks) {
    }

    public record QuestlineRequest(@NotBlank @Size(max = 1000) String goal) {
    }

    public record SplitRequest(
            @NotBlank @Size(max = 200) String taskTitle,
            @Size(max = 500) String taskDescription,
            @Size(max = 1000) String goal) {
    }
}
