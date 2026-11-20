package com.focusforge.ai.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusforge.ai.planner.PlanDtos.Difficulty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanParserTest {

    private final PlanParser parser = new PlanParser(new ObjectMapper());

    private static String tasks(String... items) {
        return "{\"questTitle\":\"Learn JWT\",\"tasks\":[" + String.join(",", items) + "]}";
    }

    private static String task(String title, int minutes, String difficulty) {
        return "{\"title\":\"" + title + "\",\"description\":\"d\",\"minutes\":" + minutes
                + ",\"difficulty\":\"" + difficulty + "\"}";
    }

    @Test
    void parsesWellFormedPlan() {
        var plan = parser.parse(tasks(task("Read RFC 7519", 12, "EASY"), task("Decode a token", 10, "MEDIUM"),
                task("Sign a token", 15, "HARD")), PlanParser.QUESTLINE, "goal");

        assertThat(plan.questTitle()).isEqualTo("Learn JWT");
        assertThat(plan.tasks()).extracting(PlanDtos.PlannedTask::difficulty)
                .containsExactly(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);
    }

    @Test
    void toleratesCodeFencesWrongCaseAndOutOfRangeMinutes() {
        String raw = "```json\n" + tasks(task("A", 90, "hard"), task("B", 1, "easy"), task("C", 12, "legendary"))
                + "\n```";
        var plan = parser.parse(raw, PlanParser.QUESTLINE, "goal");

        assertThat(plan.tasks()).extracting(PlanDtos.PlannedTask::minutes).containsExactly(15, 10, 12);
        assertThat(plan.tasks()).extracting(PlanDtos.PlannedTask::difficulty)
                .containsExactly(Difficulty.HARD, Difficulty.EASY, Difficulty.MEDIUM);
    }

    @Test
    void splitShapeForcesMicroDifficultyAndShortMinutes() {
        var plan = parser.parse(tasks(task("A", 10, "HARD"), task("B", 4, "EASY"), task("C", 1, "MEDIUM")),
                PlanParser.SPLIT, "task");
        assertThat(plan.tasks()).allMatch(t -> t.difficulty() == Difficulty.MICRO);
        assertThat(plan.tasks()).extracting(PlanDtos.PlannedTask::minutes).containsExactly(5, 4, 3);
    }

    @Test
    void capsTaskCountAndSkipsBlankTitles() {
        String[] many = new String[14];
        for (int i = 0; i < many.length; i++) {
            many[i] = task(i == 0 ? " " : "T" + i, 10, "EASY");
        }
        var plan = parser.parse(tasks(many), PlanParser.QUESTLINE, "goal");
        assertThat(plan.tasks()).hasSize(10);
        assertThat(plan.tasks().get(0).title()).isEqualTo("T1");
    }

    @Test
    void usesFallbackTitleWhenModelOmitsIt() {
        var plan = parser.parse("{\"tasks\":[" + task("A", 10, "EASY") + "," + task("B", 10, "EASY") + ","
                + task("C", 10, "EASY") + "]}", PlanParser.QUESTLINE, "Learn Kafka");
        assertThat(plan.questTitle()).isEqualTo("Learn Kafka");
    }

    @Test
    void rejectsNonJsonMissingTasksAndTooFewTasks() {
        assertThatThrownBy(() -> parser.parse("Sure! Here's your plan:", PlanParser.QUESTLINE, "g"))
                .isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> parser.parse("{\"steps\":[]}", PlanParser.QUESTLINE, "g"))
                .isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> parser.parse(tasks(task("A", 10, "EASY")), PlanParser.QUESTLINE, "g"))
                .isInstanceOf(AiResponseInvalidException.class);
    }
}
