package com.focusforge.quest.focus;

import com.focusforge.quest.quest.MicroTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "focus_sessions")
public class FocusSession {

    public enum Status { ACTIVE, COMPLETED, ABANDONED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private MicroTask task;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes;

    @Column(name = "focus_minutes")
    private Integer focusMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    protected FocusSession() {
    }

    public static FocusSession start(MicroTask task, Instant now) {
        var s = new FocusSession();
        s.id = UUID.randomUUID();
        s.userId = task.getUserId();
        s.task = task;
        s.startedAt = now;
        s.plannedMinutes = task.getMinutes();
        s.status = Status.ACTIVE;
        return s;
    }

    /** For tasks checked off without running the timer: credit the planned time. */
    public static FocusSession completedWithoutTimer(MicroTask task, Instant now) {
        var s = start(task, now.minus(Duration.ofMinutes(task.getMinutes())));
        s.finish(now, task.getMinutes());
        return s;
    }

    /**
     * Ends the session and records focused minutes (at least 1, at most 3x the
     * plan so a forgotten timer doesn't fake a 9-hour session).
     */
    public int complete(Instant now) {
        long elapsed = Math.max(1, (long) Math.ceil(Duration.between(startedAt, now).toSeconds() / 60.0));
        int minutes = (int) Math.min(elapsed, plannedMinutes * 3L);
        finish(now, minutes);
        return minutes;
    }

    public void abandon(Instant now) {
        status = Status.ABANDONED;
        endedAt = now;
    }

    private void finish(Instant now, int minutes) {
        status = Status.COMPLETED;
        endedAt = now;
        focusMinutes = minutes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public MicroTask getTask() {
        return task;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public Integer getFocusMinutes() {
        return focusMinutes;
    }

    public Status getStatus() {
        return status;
    }
}
