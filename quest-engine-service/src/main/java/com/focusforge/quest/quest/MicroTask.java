package com.focusforge.quest.quest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "micro_tasks")
public class MicroTask {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED, ABANDONED }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id")
    private Quest quest;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "parent_task_id")
    private UUID parentTaskId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int minutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private int coins;

    @Column(nullable = false)
    private int xp;

    @Column(name = "hp_restore", nullable = false)
    private int hpRestore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Guards concurrent complete/abandon (Phase 3). */
    @Version
    private Long version;

    protected MicroTask() {
    }

    MicroTask(Quest quest, int position, String title, String description, int minutes, Difficulty difficulty) {
        this.id = UUID.randomUUID();
        this.quest = quest;
        this.userId = quest.getUserId();
        this.position = position;
        this.title = title;
        this.description = description == null ? "" : description;
        this.minutes = minutes;
        this.difficulty = difficulty;
        this.coins = difficulty.coins();
        this.xp = difficulty.xp();
        this.hpRestore = difficulty.hpRestore();
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentTaskId() {
        return parentTaskId;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getMinutes() {
        return minutes;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getCoins() {
        return coins;
    }

    public int getXp() {
        return xp;
    }

    public int getHpRestore() {
        return hpRestore;
    }

    public Status getStatus() {
        return status;
    }
}
