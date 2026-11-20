CREATE TABLE rewards (
    id            UUID PRIMARY KEY,
    -- NULL = global reward curated by an admin; otherwise a player's own custom reward.
    owner_user_id UUID,
    name          VARCHAR(60)  NOT NULL,
    description   VARCHAR(200) NOT NULL DEFAULT '',
    emoji         VARCHAR(16)  NOT NULL DEFAULT '🎁',
    price         INT          NOT NULL CHECK (price BETWEEN 1 AND 100000),
    -- The DB refuses negative stock even if the application had a race bug.
    stock         INT          NOT NULL CHECK (stock >= 0),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_rewards_owner ON rewards (owner_user_id) WHERE active;

CREATE TABLE redemptions (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL,
    reward_id       UUID        NOT NULL REFERENCES rewards (id),
    reward_name     VARCHAR(60) NOT NULL,
    price           INT         NOT NULL,
    balance_after   BIGINT      NOT NULL,
    -- DB-backed idempotency: one redemption per (player, key), race-proof.
    idempotency_key VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_redemption_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX ix_redemptions_user_created ON redemptions (user_id, created_at DESC);

-- Transactional outbox (shared focus-outbox module) for RewardRedeemedEvent.
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    msg_key      VARCHAR(100) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
