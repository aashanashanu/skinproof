CREATE TABLE reminder_preferences (
    user_id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    timezone_id VARCHAR(64) NOT NULL,
    preferred_weekday SMALLINT NOT NULL CHECK (preferred_weekday BETWEEN 1 AND 7),
    preferred_time TIME NOT NULL,
    cadence VARCHAR(16) NOT NULL CHECK (cadence IN ('WEEKLY', 'BIWEEKLY')),
    opted_in_at TIMESTAMPTZ,
    unsubscribed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);