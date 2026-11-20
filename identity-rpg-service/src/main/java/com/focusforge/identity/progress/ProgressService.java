package com.focusforge.identity.progress;

import com.focusforge.events.LevelUpEvent;
import com.focusforge.events.TaskAbandonedEvent;
import com.focusforge.events.TaskCompletedEvent;
import com.focusforge.events.Topics;
import com.focusforge.identity.config.RpgClock;
import com.focusforge.identity.profile.PlayerProfile;
import com.focusforge.identity.profile.PlayerProfileRepository;
import com.focusforge.identity.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies gameplay events to the player's profile.
 * <p>
 * Side effects that must not happen if the DB change rolls back (cache eviction,
 * the LevelUpEvent) run in {@code afterCommit}. LevelUpEvent is "best effort":
 * it only drives a celebration toast, so a lost one is harmless; money-moving
 * events (TaskCompleted) go through quest-engine's transactional outbox instead.
 */
@Service
public class ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressService.class);

    private final PlayerProfileRepository profiles;
    private final ProcessedEventRepository processed;
    private final CacheManager caches;
    private final KafkaTemplate<String, Object> kafka;
    private final RpgClock clock;

    public ProgressService(PlayerProfileRepository profiles, ProcessedEventRepository processed,
                           CacheManager caches, KafkaTemplate<String, Object> kafka, RpgClock clock) {
        this.profiles = profiles;
        this.processed = processed;
        this.caches = caches;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Transactional
    public void onTaskCompleted(TaskCompletedEvent event) {
        Optional<PlayerProfile> maybe = claim(event.eventId(), event.userId());
        if (maybe.isEmpty()) {
            return;
        }
        PlayerProfile profile = maybe.get();
        LocalDate today = clock.today();
        int before = profile.getLevel();
        var progress = profile.applyCompletion(event.hpRestored(), event.xpEarned(), today);
        log.info("Player {} +{} XP +{} HP -> level {} ({} xp), hp {}", event.userId(), event.xpEarned(),
                event.hpRestored(), progress.level(), progress.xpInLevel(), profile.getHp());

        afterCommit(() -> {
            evictDashboard(event.userId(), today);
            if (progress.leveledUp()) {
                kafka.send(Topics.LEVEL_UP, event.userId().toString(),
                        LevelUpEvent.of(event.userId(), before, progress.level()));
            }
        });
    }

    @Transactional
    public void onTaskAbandoned(TaskAbandonedEvent event) {
        Optional<PlayerProfile> maybe = claim(event.eventId(), event.userId());
        if (maybe.isEmpty()) {
            return;
        }
        LocalDate today = clock.today();
        maybe.get().drainHp(event.hpLost(), today);
        afterCommit(() -> evictDashboard(event.userId(), today));
    }

    /** Dedup + load. Empty if the event was already applied or the player doesn't exist. */
    private Optional<PlayerProfile> claim(UUID eventId, UUID userId) {
        if (processed.markProcessed(eventId) == 0) {
            log.info("Duplicate event {} ignored", eventId);
            return Optional.empty();
        }
        Optional<PlayerProfile> profile = profiles.findById(userId);
        if (profile.isEmpty()) {
            log.warn("Event {} for unknown player {} skipped", eventId, userId);
        }
        return profile;
    }

    private void evictDashboard(UUID userId, LocalDate today) {
        var cache = caches.getCache(ProfileService.DASHBOARD_CACHE);
        if (cache != null) {
            cache.evict(userId + ":" + today); // same key format as @Cacheable in ProfileService
        }
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
