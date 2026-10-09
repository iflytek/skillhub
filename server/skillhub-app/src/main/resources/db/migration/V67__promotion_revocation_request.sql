ALTER TABLE promotion_request
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by VARCHAR(128),
    ADD COLUMN target_skill_id_snapshot BIGINT,
    ADD COLUMN target_version_id_snapshot BIGINT;

CREATE TABLE promotion_revocation_request (
    id BIGSERIAL PRIMARY KEY,
    initial_promotion_request_id BIGINT NOT NULL,
    source_skill_id BIGINT NOT NULL,
    target_skill_id BIGINT NOT NULL,
    source_namespace_id BIGINT NOT NULL,
    target_namespace_id BIGINT NOT NULL,
    skill_slug VARCHAR(100) NOT NULL,
    submitted_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    reviewed_by VARCHAR(128) REFERENCES user_account(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version INT NOT NULL DEFAULT 1,
    reason TEXT,
    review_comment TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_promotion_revocation_pending_target
    ON promotion_revocation_request(target_skill_id) WHERE status = 'PENDING';
CREATE INDEX idx_promotion_revocation_source ON promotion_revocation_request(source_skill_id, submitted_at DESC);
