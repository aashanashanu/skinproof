CREATE TABLE noise_calibrations (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    concern VARCHAR(32) NOT NULL,
    noise_floor DOUBLE PRECISION NOT NULL CHECK (noise_floor >= 0),
    valid_sample_count INTEGER NOT NULL CHECK (valid_sample_count >= 0),
    residual_based BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT noise_calibrations_experiment_concern_unique UNIQUE (experiment_id, concern)
);

CREATE TABLE verdicts (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    concern VARCHAR(32) NOT NULL,
    label VARCHAR(32) NOT NULL CHECK (label IN ('IMPROVED', 'NO_DETECTABLE_CHANGE', 'WORSE', 'INCONCLUSIVE')),
    confidence VARCHAR(16) NOT NULL CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    baseline_mean DOUBLE PRECISION,
    current_mean DOUBLE PRECISION,
    delta DOUBLE PRECISION,
    noise_floor DOUBLE PRECISION NOT NULL CHECK (noise_floor >= 0),
    baseline_capture_count INTEGER NOT NULL,
    valid_capture_count INTEGER NOT NULL,
    follow_up_capture_count INTEGER NOT NULL,
    elapsed_weeks INTEGER NOT NULL,
    residual_noise BOOLEAN NOT NULL,
    explanation TEXT NOT NULL,
    confound_flags_json TEXT NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX verdicts_experiment_concern_computed_idx
    ON verdicts (experiment_id, concern, computed_at DESC);