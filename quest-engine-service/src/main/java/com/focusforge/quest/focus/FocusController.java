package com.focusforge.quest.focus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusforge.quest.focus.FocusDtos.AbandonResult;
import com.focusforge.quest.focus.FocusDtos.CompletionResult;
import com.focusforge.quest.focus.FocusDtos.SessionDto;
import com.focusforge.quest.idempotency.IdempotencyStore;
import com.focusforge.quest.idempotency.IdempotencyStore.Claim;
import com.focusforge.web.ApiException;
import com.focusforge.web.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/quests")
public class FocusController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";

    private final FocusService focus;
    private final IdempotencyStore idempotency;
    private final ObjectMapper mapper;

    public FocusController(FocusService focus, IdempotencyStore idempotency, ObjectMapper mapper) {
        this.focus = focus;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    @PostMapping("/tasks/{taskId}/start")
    public SessionDto start(@CurrentUser UUID userId, @PathVariable UUID taskId) {
        try {
            return focus.start(userId, taskId);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("You already have a focus session running.");
        }
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<SessionDto> active(@CurrentUser UUID userId) {
        return focus.active(userId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /**
     * Completes a task exactly once. The client sends a fresh Idempotency-Key per
     * logical attempt and re-sends the SAME key when retrying after a timeout or
     * network error; the original response is then replayed and no coins move twice.
     */
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<CompletionResult> complete(@CurrentUser UUID userId, @PathVariable UUID taskId,
                                                     @RequestHeader(IDEMPOTENCY_KEY) String key) {
        if (!IdempotencyStore.isValidKey(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key",
                    "Idempotency-Key must be 8-100 characters of [A-Za-z0-9_-]; a UUID is ideal.");
        }
        String fingerprint = "complete:" + taskId;

        Claim claim = idempotency.claim(userId, key, fingerprint);
        switch (claim) {
            case Claim.Replay replay -> {
                return ResponseEntity.ok().header(REPLAYED, "true")
                        .body(mapper.convertValue(replay.body(), CompletionResult.class));
            }
            case Claim.InFlight ignored -> throw ApiException.conflict(
                    "A request with this Idempotency-Key is still being processed. Retry shortly.");
            case Claim.Mismatch ignored -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key Reused", "This Idempotency-Key was already used for a different request.");
            case Claim.Claimed ignored -> { }
            case Claim.Unavailable ignored -> { }
        }

        try {
            CompletionResult result = focus.complete(userId, taskId);
            idempotency.complete(userId, key, fingerprint, result);
            return ResponseEntity.ok(result);
        } catch (ObjectOptimisticLockingFailureException e) {
            idempotency.release(userId, key);
            throw ApiException.conflict("This task was completed by another request.");
        } catch (RuntimeException e) {
            idempotency.release(userId, key);
            throw e;
        }
    }

    @PostMapping("/tasks/{taskId}/abandon")
    public AbandonResult abandon(@CurrentUser UUID userId, @PathVariable UUID taskId) {
        try {
            return focus.abandon(userId, taskId);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict("This task changed while you were abandoning it. Refresh and try again.");
        }
    }
}
