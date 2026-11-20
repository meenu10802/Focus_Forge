CREATE TABLE focus_sessions (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL,
    task_id       UUID        NOT NULL REFERENCES micro_tasks (id) ON DELETE CASCADE,
    started_at    TIMESTAMPTZ NOT NULL,
    ended_at      TIMESTAMPTZ,
    planned_minutes INT       NOT NULL,
    -- Actual focused minutes, recorded on completion; feeds the Phase 6 heatmap.
    focus_minutes INT,
    status        VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED'))
);

-- A player can focus on one thing at a time. Enforced by the DB, so two racing
-- "start" requests can't both win.
CREATE UNIQUE INDEX ux_one_active_session_per_user ON focus_sessions (user_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_sessions_user_ended ON focus_sessions (user_id, ended_at);

-- Transactional outbox: events are written in the SAME transaction as the state
-- change, then relayed to Kafka. No lost events, no phantom events.
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,          -- = the event's eventId
    topic        VARCHAR(100) NOT NULL,
    msg_key      VARCHAR(100) NOT NULL,     -- userId: keeps a player's events ordered
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
