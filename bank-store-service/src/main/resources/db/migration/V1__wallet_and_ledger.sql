CREATE TABLE wallets (
    user_id    UUID PRIMARY KEY,
    -- The DB refuses a negative balance even if application code had a bug.
    balance    BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only: rows are never updated or deleted. SUM(signed amount) per user
-- must always equal wallets.balance (reconciliation invariant).
CREATE TABLE ledger_entries (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL,
    type          VARCHAR(8)   NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    amount        BIGINT       NOT NULL CHECK (amount > 0),
    balance_after BIGINT       NOT NULL CHECK (balance_after >= 0),
    reason        VARCHAR(120) NOT NULL,
    ref_id        UUID,
    -- The Kafka event that caused this entry: a second line for the same event is impossible.
    event_id      UUID UNIQUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_ledger_user_created ON ledger_entries (user_id, created_at DESC);

-- Idempotent consumer: eventIds this service has already applied.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
