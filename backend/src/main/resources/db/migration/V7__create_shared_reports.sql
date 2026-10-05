CREATE TABLE shared_reports (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    owner_type VARCHAR(16) NOT NULL CHECK (owner_type IN ('DEMO', 'USER')),
    owner_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    snapshot_json TEXT NOT NULL,
    image_keys_json TEXT NOT NULL,
    include_images BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT shared_reports_expiry_after_create CHECK (expires_at > created_at)
);

CREATE INDEX shared_reports_owner_created_idx
    ON shared_reports (owner_type, owner_id, created_at DESC);

CREATE INDEX shared_reports_experiment_created_idx
    ON shared_reports (experiment_id, created_at DESC);