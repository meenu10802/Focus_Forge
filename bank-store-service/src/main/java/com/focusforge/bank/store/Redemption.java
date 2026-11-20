package com.focusforge.bank.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "redemptions")
public class Redemption {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "reward_id", nullable = false, updatable = false)
    private UUID rewardId;

    @Column(name = "reward_name", nullable = false, updatable = false)
    private String rewardName;

    @Column(nullable = false, updatable = false)
    private int price;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Redemption() {
    }

    public Redemption(UUID userId, Reward reward, long balanceAfter, String idempotencyKey, Instant at) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.rewardId = reward.getId();
        this.rewardName = reward.getName();
        this.price = reward.getPrice();
        this.balanceAfter = balanceAfter;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRewardId() {
        return rewardId;
    }

    public String getRewardName() {
        return rewardName;
    }

    public int getPrice() {
        return price;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
