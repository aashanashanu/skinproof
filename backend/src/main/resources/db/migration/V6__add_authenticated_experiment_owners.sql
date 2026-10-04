ALTER TABLE experiments
    ALTER COLUMN workspace_id DROP NOT NULL;

ALTER TABLE experiments
    ADD COLUMN owner_type VARCHAR(16) NOT NULL DEFAULT 'DEMO',
    ADD COLUMN owner_id UUID;

UPDATE experiments
SET owner_id = workspace_id;

ALTER TABLE experiments
    ALTER COLUMN owner_id SET NOT NULL,
    ADD CONSTRAINT experiments_owner_type_check CHECK (owner_type IN ('DEMO', 'USER')),
    ADD CONSTRAINT experiments_owner_identity_check CHECK (
        (owner_type = 'DEMO' AND workspace_id IS NOT NULL AND owner_id = workspace_id)
        OR (owner_type = 'USER' AND workspace_id IS NULL)
    );

CREATE INDEX experiments_owner_created_idx
    ON experiments (owner_type, owner_id, created_at DESC);