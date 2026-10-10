CREATE TABLE system_setting (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(128) NOT NULL UNIQUE,
    value_json JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128)
);

CREATE TABLE external_role_grant_rule (
    id BIGSERIAL PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    normalized_email VARCHAR(256) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES role(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    matched_subject VARCHAR(256),
    granted_user_id VARCHAR(128) REFERENCES user_account(id),
    granted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128),
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_external_role_grant_active_identity
    ON external_role_grant_rule (provider_code, normalized_email)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_external_role_grant_status ON external_role_grant_rule (status);
