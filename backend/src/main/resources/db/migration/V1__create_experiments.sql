CREATE TABLE experiments (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    target_concern VARCHAR(32) NOT NULL,
    start_date DATE NOT NULL,
    duration_weeks INTEGER NOT NULL CHECK (duration_weeks BETWEEN 1 AND 52),
    protocol_notes VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX experiments_workspace_created_idx
    ON experiments (workspace_id, created_at DESC);