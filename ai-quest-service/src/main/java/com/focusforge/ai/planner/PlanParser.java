package com.focusforge.ai.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusforge.ai.planner.PlanDtos.Difficulty;
import com.focusforge.ai.planner.PlanDtos.PlannedTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns untrusted model output into a sane plan: tolerant of small format
 * slips (code fences, wrong-case enums, out-of-range minutes), strict about
 * structure (must have enough tasks with real titles).
 */
final class PlanParser {

    record Shape(int minTasks, int maxTasks, int minMinutes, int maxMinutes, Difficulty forcedDifficulty) {
    }

    static final Shape QUESTLINE = new Shape(3, 10, 10, 15, null);
    static final Shape SPLIT = new Shape(2, 4, 3, 5, Difficulty.MICRO);

    record ParsedPlan(String questTitle, List<PlannedTask> tasks) {
    }

    private final ObjectMapper mapper;

    PlanParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ParsedPlan parse(String raw, Shape shape, String fallbackTitle) {
        JsonNode root;
        try {
            root = mapper.readTree(stripFences(raw));
        } catch (Exception e) {
            throw new AiResponseInvalidException("Model output is not JSON", e);
        }
        JsonNode tasksNode = root == null ? null : root.get("tasks");
        if (tasksNode == null || !tasksNode.isArray()) {
            throw new AiResponseInvalidException("Model output has no 'tasks' array");
        }

        List<PlannedTask> tasks = new ArrayList<>();
        for (JsonNode t : tasksNode) {
            if (tasks.size() == shape.maxTasks()) {
                break;
            }
            String title = clip(text(t, "title"), 80);
            if (title.isBlank()) {
                continue;
            }
            int minutes = clamp(t.path("minutes").asInt(shape.maxMinutes()), shape.minMinutes(), shape.maxMinutes());
            Difficulty difficulty = shape.forcedDifficulty() != null
                    ? shape.forcedDifficulty()
                    : difficultyOf(text(t, "difficulty"));
            tasks.add(new PlannedTask(title, clip(text(t, "description"), 200), minutes, difficulty));
        }
        if (tasks.size() < shape.minTasks()) {
            throw new AiResponseInvalidException("Model returned " + tasks.size()
                    + " usable tasks; need at least " + shape.minTasks());
        }

        String questTitle = clip(text(root, "questTitle"), 60);
        return new ParsedPlan(questTitle.isBlank() ? clip(fallbackTitle, 60) : questTitle, tasks);
    }

    private static String stripFences(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return s;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText().strip();
    }

    private static String clip(String s, int max) {
        String clean = s == null ? "" : s.replaceAll("\\s+", " ").strip();
        return clean.length() <= max ? clean : clean.substring(0, max - 1).strip() + "…";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Difficulty difficultyOf(String s) {
        try {
            Difficulty d = Difficulty.valueOf(s.toUpperCase(Locale.ROOT));
            return d == Difficulty.MICRO ? Difficulty.EASY : d;
        } catch (IllegalArgumentException e) {
            return Difficulty.MEDIUM;
        }
    }
}
