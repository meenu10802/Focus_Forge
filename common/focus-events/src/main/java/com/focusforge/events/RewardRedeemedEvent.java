package com.focusforge.events;

import java.time.Instant;
import java.util.UUID;

/** Published by bank-store-service after a successful redemption. Topic: {@link Topics#REWARD_REDEEMED}. */
public record RewardRedeemedEvent(
        UUID eventId,
        UUID userId,
        Instant occurredAt,
        UUID rewardId,
        String rewardName,
        int price,
        int remainingStock,
        long newBalance
) implements FocusEvent {

    public static RewardRedeemedEvent of(UUID userId, UUID rewardId, String rewardName,
                                         int price, int remainingStock, long newBalance) {
        return new RewardRedeemedEvent(UUID.randomUUID(), userId, Instant.now(),
                rewardId, rewardName, price, remainingStock, newBalance);
    }
}
