package com.focusforge.identity.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "player_profiles")
public class PlayerProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private int hp;

    @Column(name = "max_hp", nullable = false)
    private int maxHp;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private long xp;

    @Column(name = "streak_days", nullable = false)
    private int streakDays;

    @Column(name = "last_streak_date")
    private LocalDate lastStreakDate;

    @Column(name = "hp_reset_on", nullable = false)
    private LocalDate hpResetOn;

    /** Optimistic lock: Kafka consumers (Phase 3) and the daily reset may race. */
    @Version
    private Long version;

    protected PlayerProfile() {
    }

    public static PlayerProfile newPlayer(UUID userId, int maxHp, LocalDate today) {
        var p = new PlayerProfile();
        p.userId = userId;
        p.maxHp = maxHp;
        p.hp = maxHp;
        p.level = 1;
        p.xp = 0;
        p.hpResetOn = today;
        return p;
    }

    /**
     * Lazy daily reset: the first touch of a new day refills HP. No cron job,
     * and correct no matter how long the player was away.
     *
     * @return true if the profile changed
     */
    public boolean refillIfNewDay(LocalDate today) {
        if (hpResetOn.isBefore(today)) {
            hp = maxHp;
            hpResetOn = today;
            return true;
        }
        return false;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getLevel() {
        return level;
    }

    public long getXp() {
        return xp;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public LocalDate getHpResetOn() {
        return hpResetOn;
    }
}
