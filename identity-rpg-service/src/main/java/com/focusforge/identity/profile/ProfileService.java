package com.focusforge.identity.profile;

import com.focusforge.identity.user.User;
import com.focusforge.identity.user.UserRepository;
import com.focusforge.web.ApiException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class ProfileService {

    public static final String DASHBOARD_CACHE = "dashboard";

    private final PlayerProfileRepository profiles;
    private final UserRepository users;

    public ProfileService(PlayerProfileRepository profiles, UserRepository users) {
        this.profiles = profiles;
        this.users = users;
    }

    /**
     * Cache key includes the day, so the first request of a new day always
     * misses the cache and applies the lazy HP refill. Writers (Kafka consumers
     * in Phase 3) evict {@code userId:today}.
     */
    @Cacheable(cacheNames = DASHBOARD_CACHE, key = "#userId + ':' + #today")
    @Transactional
    public DashboardDto getDashboard(UUID userId, LocalDate today) {
        PlayerProfile profile = profiles.findById(userId)
                .orElseThrow(() -> ApiException.notFound("No profile for this player."));
        profile.refillIfNewDay(today); // flushed on commit if changed

        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("No such player."));

        return new DashboardDto(
                userId,
                user.getDisplayName(),
                user.getRole().name(),
                profile.getLevel(),
                profile.getHp(),
                profile.getMaxHp(),
                profile.getXp(),
                LevelingPolicy.xpToAdvance(profile.getLevel()),
                profile.getStreakDays());
    }
}
