CREATE TABLE analyses (
    id UUID PRIMARY KEY,
    cache_key CHAR(64) NOT NULL UNIQUE,
    concern_actions VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'SUCCEEDED', 'RETRY_WAIT', 'FAILED')),
    provider_task_id VARCHAR(128),
    concern_scores_json TEXT,
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    documented_unit_estimate INTEGER NOT NULL,
    retry_after TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX analyses_worker_queue_idx
    ON analyses (status, retry_after, created_at);

CREATE TABLE captures (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id),
    image_key VARCHAR(512) NOT NULL,
    image_sha256 CHAR(64) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    quality_score INTEGER NOT NULL CHECK (quality_score BETWEEN 0 AND 100),
    quality_flags_json TEXT NOT NULL,
    is_baseline BOOLEAN NOT NULL DEFAULT FALSE,
    is_valid BOOLEAN NOT NULL DEFAULT FALSE,
    simulated BOOLEAN NOT NULL DEFAULT FALSE,
    taken_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX captures_experiment_taken_idx
    ON captures (experiment_id, taken_at);

CREATE INDEX captures_image_sha_idx
    ON captures (image_sha256);