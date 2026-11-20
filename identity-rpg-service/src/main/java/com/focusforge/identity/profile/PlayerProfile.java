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

    /** Rewards for a completed micro-task: HP (capped at max) and XP with level roll-over. */
    public LevelingPolicy.Progress applyCompletion(int hpRestored, int xpEarned, LocalDate today) {
        refillIfNewDay(today);
        hp = Math.min(maxHp, hp + Math.max(0, hpRestored));
        var progress = LevelingPolicy.gain(level, xp, Math.max(0, xpEarned));
        level = progress.level();
        xp = progress.xpInLevel();
        return progress;
    }

    /** Penalty for abandoning a focus session. HP floors at 0. */
    public void drainHp(int amount, LocalDate today) {
        refillIfNewDay(today);
        hp = Math.max(0, hp - Math.max(0, amount));
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
