-- Durable two-stage Suite Bundle workflow. Preview rows never reserve a target; only active
-- execution operations participate in the partial unique reservation index.
CREATE TABLE skill_suite_bundle_preview (
    token VARCHAR(64) PRIMARY KEY,
    actor_id VARCHAR(128) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    target_suite_slug VARCHAR(128) NOT NULL,
    target_suite_id BIGINT REFERENCES skill_suite(id) ON DELETE SET NULL,
    base_suite_version_id BIGINT REFERENCES skill_suite_version(id) ON DELETE SET NULL,
    target_version VARCHAR(64) NOT NULL,
    archive_object_key VARCHAR(1024) NOT NULL,
    archive_sha256 VARCHAR(64) NOT NULL,
    manifest_json JSONB NOT NULL,
    plan_json JSONB NOT NULL,
    warning_digest VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_suite_bundle_preview_actor_created
    ON skill_suite_bundle_preview(actor_id, created_at DESC);
CREATE INDEX idx_suite_bundle_preview_expiry
    ON skill_suite_bundle_preview(expires_at)
    WHERE status = 'PREVIEW_READY';

CREATE TABLE skill_suite_bundle_operation (
    operation_id VARCHAR(64) PRIMARY KEY,
    preview_token VARCHAR(64) NOT NULL UNIQUE REFERENCES skill_suite_bundle_preview(token),
    client_request_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    target_suite_slug VARCHAR(128) NOT NULL,
    target_suite_id BIGINT REFERENCES skill_suite(id) ON DELETE SET NULL,
    base_suite_version_id BIGINT REFERENCES skill_suite_version(id) ON DELETE SET NULL,
    target_version VARCHAR(64) NOT NULL,
    reservation_key VARCHAR(256) NOT NULL,
    reservation_active BOOLEAN NOT NULL DEFAULT TRUE,
    archive_object_key VARCHAR(1024) NOT NULL,
    archive_sha256 VARCHAR(64) NOT NULL,
    plan_json JSONB NOT NULL,
    warning_digest VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(128),
    failure_detail TEXT,
    result_suite_id BIGINT REFERENCES skill_suite(id) ON DELETE SET NULL,
    result_suite_version_id BIGINT REFERENCES skill_suite_version(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_suite_bundle_operation_actor_request UNIQUE (actor_id, client_request_id)
);

CREATE UNIQUE INDEX uk_suite_bundle_operation_active_reservation
    ON skill_suite_bundle_operation(reservation_key)
    WHERE reservation_active = TRUE;
CREATE INDEX idx_suite_bundle_operation_actor_created
    ON skill_suite_bundle_operation(actor_id, created_at DESC);
CREATE INDEX idx_suite_bundle_operation_recovery
    ON skill_suite_bundle_operation(status, updated_at)
    WHERE status IN ('RUNNING', 'WAITING_FOR_MEMBERS', 'BLOCKED_RETRYABLE');

CREATE TABLE skill_suite_bundle_member_result (
    id BIGSERIAL PRIMARY KEY,
    operation_id VARCHAR(64) NOT NULL REFERENCES skill_suite_bundle_operation(operation_id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position >= 0),
    namespace_slug VARCHAR(128) NOT NULL,
    skill_slug VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    package_path VARCHAR(1024),
    requested_visibility VARCHAR(32),
    requested_version VARCHAR(64),
    relationship_change VARCHAR(32) NOT NULL,
    publish_action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(255),
    skill_id BIGINT REFERENCES skill(id) ON DELETE SET NULL,
    skill_version_id BIGINT REFERENCES skill_version(id) ON DELETE SET NULL,
    errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_suite_bundle_member_position UNIQUE (operation_id, position),
    CONSTRAINT uk_suite_bundle_member_skill UNIQUE (operation_id, namespace_slug, skill_slug),
    CONSTRAINT ck_suite_bundle_member_source CHECK (
        (source_type = 'PACKAGE' AND package_path IS NOT NULL)
        OR (source_type = 'REFERENCE' AND package_path IS NULL AND requested_version IS NOT NULL)
    )
);

CREATE INDEX idx_suite_bundle_member_operation_status
    ON skill_suite_bundle_member_result(operation_id, status);
