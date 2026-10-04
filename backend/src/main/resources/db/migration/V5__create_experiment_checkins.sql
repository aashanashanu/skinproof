CREATE TABLE experiment_checkins (
    id UUID PRIMARY KEY,
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    check_in_date DATE NOT NULL,
    reported_confounds_json TEXT NOT NULL,
    automatic_suggestions_json TEXT NOT NULL,
    confirmed_suggestions_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT experiment_checkins_experiment_date_unique UNIQUE (experiment_id, check_in_date)
);

CREATE INDEX experiment_checkins_experiment_date_idx
    ON experiment_checkins (experiment_id, check_in_date DESC);
