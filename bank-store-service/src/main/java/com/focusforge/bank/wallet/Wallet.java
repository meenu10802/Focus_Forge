package com.focusforge.bank.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private long balance;

    /** Optimistic lock: concurrent credit/debit (Phase 4 redemptions) can't lose an update. */
    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public long credit(long amount, Instant at) {
        if (amount <= 0) {
            throw new IllegalArgumentException("credit amount must be positive");
        }
        balance = Math.addExact(balance, amount);
        updatedAt = at;
        return balance;
    }

    /** @throws IllegalStateException if the balance would go negative (callers check first). */
    public long debit(long amount, Instant at) {
        if (amount <= 0) {
            throw new IllegalArgumentException("debit amount must be positive");
        }
        if (balance < amount) {
            throw new IllegalStateException("insufficient balance");
        }
        balance -= amount;
        updatedAt = at;
        return balance;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
