-- Keeps Suite install-plan counters idempotent across safe client retries.
CREATE TABLE skill_suite_install_operation (
    operation_id VARCHAR(64) PRIMARY KEY,
    client_request_id VARCHAR(64) NOT NULL,
    actor_key VARCHAR(160) NOT NULL,
    suite_id BIGINT NOT NULL,
    suite_version_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_suite_install_client_actor UNIQUE (client_request_id, actor_key)
);

CREATE INDEX idx_skill_suite_install_operation_created_at
    ON skill_suite_install_operation(created_at);
