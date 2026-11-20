package com.focusforge.identity.profile;

/**
 * Pure leveling math (no Spring) so it is trivially unit-testable.
 * <p>
 * XP needed to advance from level {@code n} to {@code n+1} is
 * {@code round(100 * n^1.5)}: 100, 283, 520, 800, 1118, ...
 */
public final class LevelingPolicy {

    public static final int MAX_LEVEL = 999;

    private LevelingPolicy() {
    }

    public static long xpToAdvance(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
        return Math.round(100 * Math.pow(level, 1.5));
    }

    /**
     * Applies earned XP, rolling over as many levels as it covers.
     *
     * @param level     current level (>= 1)
     * @param xpInLevel XP already earned inside the current level
     * @param gained    XP to add (>= 0)
     */
    public static Progress gain(int level, long xpInLevel, long gained) {
        if (gained < 0) {
            throw new IllegalArgumentException("gained must be >= 0");
        }
        int newLevel = level;
        long xp = xpInLevel + gained;
        while (newLevel < MAX_LEVEL && xp >= xpToAdvance(newLevel)) {
            xp -= xpToAdvance(newLevel);
            newLevel++;
        }
        return new Progress(newLevel, xp, newLevel - level);
    }

    public record Progress(int level, long xpInLevel, int levelsGained) {

        public boolean leveledUp() {
            return levelsGained > 0;
        }
    }
}
