package com.focusforge.events;

/** Kafka topic names. Message key for every topic is the player's userId. */
public final class Topics {

    public static final String TASK_COMPLETED = "quest.task-completed";
    public static final String TASK_ABANDONED = "quest.task-abandoned";
    public static final String LEVEL_UP = "rpg.level-up";
    public static final String STREAK_MILESTONE = "rpg.streak-milestone";
    public static final String REWARD_REDEEMED = "store.reward-redeemed";

    /** Suffix used by the DefaultErrorHandler's DeadLetterPublishingRecoverer. */
    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }
}
