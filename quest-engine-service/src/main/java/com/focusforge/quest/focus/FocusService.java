package com.focusforge.quest.focus;

import com.focusforge.events.TaskAbandonedEvent;
import com.focusforge.events.TaskCompletedEvent;
import com.focusforge.events.Topics;
import com.focusforge.quest.focus.FocusDtos.AbandonResult;
import com.focusforge.quest.focus.FocusDtos.CompletionResult;
import com.focusforge.quest.focus.FocusDtos.SessionDto;
import com.focusforge.outbox.Outbox;
import com.focusforge.quest.quest.MicroTask;
import com.focusforge.quest.quest.MicroTaskRepository;
import com.focusforge.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Single-transaction state changes. Idempotency-key handling lives a layer above. */
@Service
public class FocusService {

    private final MicroTaskRepository tasks;
    private final FocusSessionRepository sessions;
    private final Outbox outbox;
    private final Clock clock;
    private final int abandonHpPenalty;

    public FocusService(MicroTaskRepository tasks, FocusSessionRepository sessions, Outbox outbox, Clock clock,
                        @Value("${focus.quests.abandon-hp-penalty:10}") int abandonHpPenalty) {
        this.tasks = tasks;
        this.sessions = sessions;
        this.outbox = outbox;
        this.clock = clock;
        this.abandonHpPenalty = abandonHpPenalty;
    }

    @Transactional
    public SessionDto start(UUID userId, UUID taskId) {
        MicroTask task = ownedTask(userId, taskId);
        if (task.isCompleted()) {
            throw ApiException.conflict("This task is already completed.");
        }
        Optional<FocusSession> active = sessions.findActive(userId);
        if (active.isPresent()) {
            if (active.get().getTask().getId().equals(taskId)) {
                return SessionDto.of(active.get()); // already running: idempotent
            }
            throw ApiException.conflict("You're already focusing on \"" + active.get().getTask().getTitle()
                    + "\". Finish or abandon it first.");
        }
        task.start();
        // saveAndFlush so a racing second start hits the partial unique index here, inside the try of the caller
        return SessionDto.of(sessions.saveAndFlush(FocusSession.start(task, clock.instant())));
    }

    @Transactional(readOnly = true)
    public Optional<SessionDto> active(UUID userId) {
        return sessions.findActive(userId).map(SessionDto::of);
    }

    @Transactional
    public CompletionResult complete(UUID userId, UUID taskId) {
        MicroTask task = ownedTask(userId, taskId);
        if (task.isCompleted()) {
            throw ApiException.conflict("This task is already completed.");
        }
        Instant now = clock.instant();
        int minutes = sessions.findActive(userId)
                .filter(s -> s.getTask().getId().equals(taskId))
                .map(s -> s.complete(now))
                .orElseGet(() -> sessions.save(FocusSession.completedWithoutTimer(task, now)).getFocusMinutes());

        task.complete(now); // @Version: a concurrent completion with another key fails at commit
        boolean questCompleted = task.getQuest().completeIfAllTasksDone();

        var event = TaskCompletedEvent.of(userId, task.getId(), task.getQuest().getId(),
                task.getCoins(), task.getXp(), task.getHpRestore(), minutes);
        outbox.append(Topics.TASK_COMPLETED, event);

        return new CompletionResult(task.getId(), task.getQuest().getId(), task.getCoins(), task.getXp(),
                task.getHpRestore(), minutes, questCompleted, event.eventId());
    }

    @Transactional
    public AbandonResult abandon(UUID userId, UUID taskId) {
        MicroTask task = ownedTask(userId, taskId);
        if (task.getStatus() != MicroTask.Status.IN_PROGRESS) {
            throw ApiException.conflict("Only a task you're currently focusing on can be abandoned.");
        }
        Instant now = clock.instant();
        sessions.findActive(userId)
                .filter(s -> s.getTask().getId().equals(taskId))
                .ifPresent(s -> s.abandon(now));
        task.abandon();

        var event = TaskAbandonedEvent.of(userId, taskId, abandonHpPenalty);
        outbox.append(Topics.TASK_ABANDONED, event);
        return new AbandonResult(taskId, abandonHpPenalty, event.eventId());
    }

    private MicroTask ownedTask(UUID userId, UUID taskId) {
        return tasks.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> ApiException.notFound("Task not found."));
    }
}
