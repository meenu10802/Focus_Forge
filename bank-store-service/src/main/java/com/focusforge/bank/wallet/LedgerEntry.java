package com.focusforge.bank.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Immutable ledger line. There are deliberately no setters. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum Type { CREDIT, DEBIT }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "ref_id", updatable = false)
    private UUID refId;

    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry credit(UUID userId, long amount, long balanceAfter, String reason, UUID refId,
                                     UUID eventId, Instant at) {
        var e = new LedgerEntry();
        e.id = UUID.randomUUID();
        e.userId = userId;
        e.type = Type.CREDIT;
        e.amount = amount;
        e.balanceAfter = balanceAfter;
        e.reason = reason;
        e.refId = refId;
        e.eventId = eventId;
        e.createdAt = at;
        return e;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRefId() {
        return refId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
