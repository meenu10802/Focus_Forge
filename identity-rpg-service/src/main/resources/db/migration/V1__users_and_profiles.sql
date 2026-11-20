CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(60)  NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('PLAYER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);

-- Emails are stored lower-cased by the app; the unique index is the race-proof guard.
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE player_profiles (
    user_id          UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    hp               INT    NOT NULL CHECK (hp >= 0),
    max_hp           INT    NOT NULL CHECK (max_hp > 0),
    level            INT    NOT NULL DEFAULT 1 CHECK (level >= 1),
    -- XP earned inside the current level (resets on level-up).
    xp               BIGINT NOT NULL DEFAULT 0 CHECK (xp >= 0),
    streak_days      INT    NOT NULL DEFAULT 0,
    last_streak_date DATE,
    -- Day (in focus.rpg.timezone) on which HP was last refilled; drives the lazy daily reset.
    hp_reset_on      DATE   NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_hp_le_max CHECK (hp <= max_hp)
);
