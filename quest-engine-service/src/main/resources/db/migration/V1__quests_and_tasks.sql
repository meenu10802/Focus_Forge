CREATE TABLE quests (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL,
    goal       VARCHAR(300) NOT NULL,
    title      VARCHAR(80)  NOT NULL,
    status     VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    source     VARCHAR(16)  NOT NULL CHECK (source IN ('AI_GENERATED', 'FALLBACK')),
    provider   VARCHAR(32)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_quests_user_created ON quests (user_id, created_at DESC);

CREATE TABLE micro_tasks (
    id             UUID PRIMARY KEY,
    quest_id       UUID         NOT NULL REFERENCES quests (id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL,
    -- Set for "I'm overwhelmed" sub-steps (Phase 6).
    parent_task_id UUID REFERENCES micro_tasks (id) ON DELETE CASCADE,
    position       INT          NOT NULL,
    title          VARCHAR(100) NOT NULL,
    description    VARCHAR(250) NOT NULL DEFAULT '',
    minutes        INT          NOT NULL CHECK (minutes BETWEEN 1 AND 60),
    difficulty     VARCHAR(8)   NOT NULL CHECK (difficulty IN ('MICRO', 'EASY', 'MEDIUM', 'HARD')),
    -- Rewards are snapshotted at creation so later balance changes don't rewrite history.
    coins          INT          NOT NULL CHECK (coins >= 0),
    xp             INT          NOT NULL CHECK (xp >= 0),
    hp_restore     INT          NOT NULL CHECK (hp_restore >= 0),
    status         VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_tasks_quest ON micro_tasks (quest_id, position);
CREATE INDEX ix_tasks_user_status ON micro_tasks (user_id, status);
