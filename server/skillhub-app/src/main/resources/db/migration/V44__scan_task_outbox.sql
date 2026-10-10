CREATE TABLE scan_task_outbox (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL,
    version_id BIGINT NOT NULL,
    skill_path VARCHAR(1000),
    bundle_key VARCHAR(1000),
    publisher_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entity_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_scan_task_outbox_task_id UNIQUE (task_id),
    CONSTRAINT ck_scan_task_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_scan_task_outbox_pending
    ON scan_task_outbox (status, next_attempt_at, created_at);
CREATE INDEX idx_scan_task_outbox_lease
    ON scan_task_outbox (status, lease_until);
CREATE INDEX idx_scan_task_outbox_version
    ON scan_task_outbox (version_id);

ALTER TABLE security_audit ADD COLUMN task_id VARCHAR(100);
CREATE INDEX idx_security_audit_task_id ON security_audit (task_id);