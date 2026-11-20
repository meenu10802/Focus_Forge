package com.focusforge.quest.quest;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quests")
public class Quest {

    public enum Status { ACTIVE, COMPLETED, ARCHIVED }

    public enum Source { AI_GENERATED, FALLBACK }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String goal;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(nullable = false)
    private String provider;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<MicroTask> tasks = new ArrayList<>();

    protected Quest() {
    }

    public Quest(UUID userId, String goal, String title, Source source, String provider) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.goal = goal;
        this.title = title;
        this.source = source;
        this.provider = provider;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public MicroTask addTask(String title, String description, int minutes, Difficulty difficulty) {
        var task = new MicroTask(this, tasks.size() + 1, title, description, minutes, difficulty);
        tasks.add(task);
        return task;
    }

    /**
     * Marks the quest COMPLETED once every top-level task is done.
     * @return true if this call completed the quest
     */
    public boolean completeIfAllTasksDone() {
        if (status != Status.ACTIVE) {
            return false;
        }
        boolean allDone = tasks.stream()
                .filter(t -> t.getParentTaskId() == null)
                .allMatch(MicroTask::isCompleted);
        if (allDone) {
            status = Status.COMPLETED;
        }
        return allDone;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getGoal() {
        return goal;
    }

    public String getTitle() {
        return title;
    }

    public Status getStatus() {
        return status;
    }

    public Source getSource() {
        return source;
    }

    public String getProvider() {
        return provider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<MicroTask> getTasks() {
        return tasks;
    }
}
