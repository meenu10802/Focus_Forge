package com.focusforge.quest.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stripe-style idempotency keys in Redis.
 * <pre>
 *   idem:{userId}:{key} = {"state":"PROCESSING","fp":"complete:{taskId}"}        TTL 60 s
 *                       -> {"state":"DONE","fp":...,"body":{...}}                TTL 24 h
 * </pre>
 * Keys are scoped per user so one player can't replay another's response.
 * The fingerprint stops a key being reused for a different request.
 * <p>
 * This layer gives clients an identical replayed response. The real
 * exactly-once guarantee for coins is the DB state transition (a task can only
 * go to COMPLETED once), so if Redis is down we fail OPEN and carry on.
 */
@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,100}");
    private static final Duration IN_FLIGHT_TTL = Duration.ofSeconds(60);
    private static final Duration DONE_TTL = Duration.ofHours(24);

    public sealed interface Claim {
        /** First time we see this key: go ahead. */
        record Claimed() implements Claim { }
        /** Seen and finished: return this body unchanged. */
        record Replay(JsonNode body) implements Claim { }
        /** Same key, still being processed by another request. */
        record InFlight() implements Claim { }
        /** Same key, but for a different operation. */
        record Mismatch() implements Claim { }
        /** Redis unavailable: proceed, relying on the DB guard. */
        record Unavailable() implements Claim { }
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public IdempotencyStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY_FORMAT.matcher(key).matches();
    }

    public Claim claim(UUID userId, String key, String fingerprint) {
        String redisKey = redisKey(userId, key);
        try {
            ObjectNode processing = mapper.createObjectNode().put("state", "PROCESSING").put("fp", fingerprint);
            Boolean fresh = redis.opsForValue().setIfAbsent(redisKey, processing.toString(), IN_FLIGHT_TTL);
            if (Boolean.TRUE.equals(fresh)) {
                return new Claim.Claimed();
            }
            String existing = redis.opsForValue().get(redisKey);
            if (existing == null) {
                return claim(userId, key, fingerprint); // expired between our two calls
            }
            JsonNode node = mapper.readTree(existing);
            if (!fingerprint.equals(node.path("fp").asText())) {
                return new Claim.Mismatch();
            }
            return "DONE".equals(node.path("state").asText())
                    ? new Claim.Replay(node.get("body"))
                    : new Claim.InFlight();
        } catch (DataAccessException e) {
            log.warn("Idempotency store unavailable, failing open: {}", e.getMessage());
            return new Claim.Unavailable();
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt idempotency record " + redisKey, e);
        }
    }

    public void complete(UUID userId, String key, String fingerprint, Object body) {
        try {
            ObjectNode done = mapper.createObjectNode().put("state", "DONE").put("fp", fingerprint);
            done.set("body", mapper.valueToTree(body));
            redis.opsForValue().set(redisKey(userId, key), done.toString(), DONE_TTL);
        } catch (DataAccessException e) {
            log.warn("Could not record idempotent response: {}", e.getMessage());
        }
    }

    /** On failure, free the key so the client can retry with it. */
    public void release(UUID userId, String key) {
        try {
            redis.delete(redisKey(userId, key));
        } catch (DataAccessException e) {
            log.warn("Could not release idempotency key: {}", e.getMessage());
        }
    }

    private static String redisKey(UUID userId, String key) {
        return "idem:" + userId + ":" + key;
    }
}
