CREATE TABLE skill_storage_delete_compensation (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT,
    namespace VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    storage_keys_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skill_storage_delete_comp_status_created
    ON skill_storage_delete_compensation (status, created_at);
