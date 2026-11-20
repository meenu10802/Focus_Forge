package com.focusforge.bank.store;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class StoreDtos {

    private StoreDtos() {
    }

    public record RewardRequest(
            @NotBlank @Size(min = 2, max = 60) String name,
            @Size(max = 200) String description,
            @Size(max = 16) String emoji,
            @Min(1) @Max(100_000) int price,
            @Min(0) @Max(10_000) int stock) {
    }

    public record RewardDto(UUID id, String name, String description, String emoji, int price, int stock,
                            boolean global, boolean mine) {

        static RewardDto of(Reward r, UUID viewer) {
            return new RewardDto(r.getId(), r.getName(), r.getDescription(), r.getEmoji(), r.getPrice(),
                    r.getStock(), r.isGlobal(), viewer != null && r.isOwnedBy(viewer));
        }
    }

    public record RedemptionDto(UUID redemptionId, UUID rewardId, String rewardName, int price, long newBalance,
                                Instant redeemedAt) {

        static RedemptionDto of(Redemption r) {
            return new RedemptionDto(r.getId(), r.getRewardId(), r.getRewardName(), r.getPrice(),
                    r.getBalanceAfter(), r.getCreatedAt());
        }
    }
}
