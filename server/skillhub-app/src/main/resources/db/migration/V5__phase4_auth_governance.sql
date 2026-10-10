CREATE TABLE local_credential (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_local_credential_username ON local_credential (username);
CREATE UNIQUE INDEX idx_local_credential_user_id ON local_credential (user_id);

CREATE TABLE account_merge_request (
    id BIGSERIAL PRIMARY KEY,
    primary_user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    secondary_user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    verification_token VARCHAR(255),
    token_expires_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merge_primary_status ON account_merge_request (primary_user_id, status);
CREATE UNIQUE INDEX idx_merge_secondary_pending
    ON account_merge_request (secondary_user_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_merge_token_pending
    ON account_merge_request (verification_token)
    WHERE status = 'PENDING';
