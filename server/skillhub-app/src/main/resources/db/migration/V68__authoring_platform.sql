-- Skill authoring platform: drafts, draft files, runtime bindings, and validation
-- runs with their persisted events and findings. Drafts feed validated content into
-- the existing publish pipeline, so these tables are authored standalone and only
-- reference namespace/skill_version loosely (no circular foreign keys).

CREATE TABLE skill_draft (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    owner_id VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    requirement TEXT,
    revision INT NOT NULL DEFAULT 1,
    content_digest VARCHAR(64) NOT NULL,
    validated_revision INT,
    validated_run_id BIGINT,
    submitted_skill_id BIGINT,
    submitted_version_id BIGINT,
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_skill_draft_revision CHECK (revision > 0)
);

-- Name uniqueness is enforced case-insensitively per owner.
CREATE UNIQUE INDEX uq_skill_draft_owner_name
    ON skill_draft(owner_id, LOWER(name));

CREATE INDEX idx_skill_draft_owner_updated
    ON skill_draft(owner_id, updated_at DESC);

CREATE INDEX idx_skill_draft_namespace
    ON skill_draft(namespace_id);

-- File bodies live in object storage under content-addressed keys; this table tracks
-- identity, digest, and size only.
CREATE TABLE draft_file (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL REFERENCES skill_draft(id) ON DELETE CASCADE,
    file_path VARCHAR(512) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    size BIGINT NOT NULL,
    content_type VARCHAR(128),
    storage_key VARCHAR(768) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_draft_file_path UNIQUE (draft_id, file_path),
    CONSTRAINT chk_draft_file_size CHECK (size >= 0)
);

CREATE INDEX idx_draft_file_draft
    ON draft_file(draft_id);

CREATE INDEX idx_draft_file_storage_key
    ON draft_file(storage_key);

-- One runtime binding per draft; secrets are referenced by name and resolved from
-- server-side configuration, never stored inline.
CREATE TABLE runtime_binding (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL REFERENCES skill_draft(id) ON DELETE CASCADE,
    agent_type VARCHAR(32) NOT NULL,
    config JSONB,
    tool_allowlist JSONB,
    mcp_servers JSONB,
    updated_by VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_runtime_binding_draft UNIQUE (draft_id)
);

CREATE TABLE validation_run (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL REFERENCES skill_draft(id) ON DELETE CASCADE,
    draft_revision INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    summary JSONB,
    triggered_by VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_validation_run_error_count CHECK (error_count >= 0),
    CONSTRAINT chk_validation_run_warning_count CHECK (warning_count >= 0)
);

CREATE INDEX idx_validation_run_draft_created
    ON validation_run(draft_id, created_at DESC);

-- Partial index keeps the active-run uniqueness cheap for the maintenance sweep.
CREATE INDEX idx_validation_run_status
    ON validation_run(status)
    WHERE status IN ('QUEUED', 'PREPARING', 'RUNNING');

CREATE INDEX idx_validation_run_stale_sweep
    ON validation_run(status, created_at);

CREATE TABLE validation_event (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES validation_run(id) ON DELETE CASCADE,
    seq INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    phase VARCHAR(32),
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_validation_event_seq UNIQUE (run_id, seq)
);

CREATE TABLE validation_finding (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES validation_run(id) ON DELETE CASCADE,
    layer VARCHAR(16) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    file_path VARCHAR(512),
    location VARCHAR(128),
    message TEXT NOT NULL,
    suggestion JSONB,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    applied_revision INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_validation_finding_status
        CHECK (status IN ('OPEN', 'APPLIED', 'DISMISSED'))
);

CREATE INDEX idx_validation_finding_run_status
    ON validation_finding(run_id, status);
