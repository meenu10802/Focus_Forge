package com.focusforge.ai.planner;

import com.focusforge.ai.planner.PlanDtos.Difficulty;
import com.focusforge.ai.planner.PlanDtos.PlanResponse;
import com.focusforge.ai.planner.PlanDtos.PlannedTask;
import com.focusforge.ai.planner.PlanDtos.Source;

import java.util.List;

/**
 * Deterministic, offline plans used when the AI is unavailable. Generic but
 * genuinely useful: a Pomodoro-style arc from orientation to teach-back.
 */
final class FallbackTemplates {

    private FallbackTemplates() {
    }

    static PlanResponse questline(String goal) {
        String topic = shorten(goal, 50);
        return new PlanResponse(
                shorten("Quest: " + goal, 60),
                Source.FALLBACK,
                "template",
                List.of(
                        new PlannedTask("Map the territory",
                                "Skim an overview of \"" + topic + "\" and list the 3-5 key ideas you see.",
                                10, Difficulty.EASY),
                        new PlannedTask("Pick your first target",
                                "Choose the single most important idea from your list and write down why it matters.",
                                10, Difficulty.EASY),
                        new PlannedTask("Deep dive",
                                "Study that one idea properly: read, watch, or follow a tutorial. Take short notes.",
                                15, Difficulty.MEDIUM),
                        new PlannedTask("Get your hands dirty",
                                "Apply it: write code, solve a problem, or do an exercise without looking at the answer.",
                                15, Difficulty.HARD),
                        new PlannedTask("Teach it back",
                                "Explain what you learned in 5 sentences as if to a friend. Note what still feels fuzzy.",
                                10, Difficulty.MEDIUM)));
    }

    static PlanResponse split(String taskTitle) {
        return new PlanResponse(
                shorten("Tiny steps: " + taskTitle, 60),
                Source.FALLBACK,
                "template",
                List.of(
                        new PlannedTask("Just open it",
                                "Open the material for \"" + shorten(taskTitle, 60) + "\". That's it — opening counts.",
                                3, Difficulty.MICRO),
                        new PlannedTask("Read one small piece",
                                "Read only the first section or example. Jot one sentence about it.",
                                5, Difficulty.MICRO),
                        new PlannedTask("Try one thing",
                                "Do the smallest possible hands-on step: one line of code, one practice question.",
                                5, Difficulty.MICRO)));
    }

    private static String shorten(String s, int max) {
        String clean = s.replaceAll("\\s+", " ").strip();
        return clean.length() <= max ? clean : clean.substring(0, max - 1).strip() + "…";
    }
}
