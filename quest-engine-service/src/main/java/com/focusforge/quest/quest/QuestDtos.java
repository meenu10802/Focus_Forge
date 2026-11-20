package com.focusforge.quest.quest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuestDtos {

    private QuestDtos() {
    }

    public record GenerateQuestRequest(
            @NotBlank @Size(min = 3, max = 300, message = "must be 3-300 characters") String goal) {
    }

    public record TaskDto(UUID id, UUID parentTaskId, int position, String title, String description, int minutes,
                          Difficulty difficulty, int coins, int xp, int hpRestore, MicroTask.Status status) {

        static TaskDto of(MicroTask t) {
            return new TaskDto(t.getId(), t.getParentTaskId(), t.getPosition(), t.getTitle(), t.getDescription(),
                    t.getMinutes(), t.getDifficulty(), t.getCoins(), t.getXp(), t.getHpRestore(), t.getStatus());
        }
    }

    public record QuestDto(UUID id, String title, String goal, Quest.Status status, Quest.Source source,
                           String provider, Instant createdAt, int totalCoins, int totalXp, int totalMinutes,
                           List<TaskDto> tasks) {

        static QuestDto of(Quest q) {
            var tasks = q.getTasks().stream().map(TaskDto::of).toList();
            return new QuestDto(q.getId(), q.getTitle(), q.getGoal(), q.getStatus(), q.getSource(), q.getProvider(),
                    q.getCreatedAt(),
                    tasks.stream().mapToInt(TaskDto::coins).sum(),
                    tasks.stream().mapToInt(TaskDto::xp).sum(),
                    tasks.stream().mapToInt(TaskDto::minutes).sum(),
                    tasks);
        }
    }
}
