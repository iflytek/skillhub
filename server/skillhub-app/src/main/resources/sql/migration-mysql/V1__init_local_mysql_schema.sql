CREATE TABLE user_account (
    id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(256),
    avatar_url VARCHAR(512),
    uss_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    merged_to_user_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_account_uss_id (uss_id),
    KEY idx_user_account_email (email),
    KEY idx_user_account_status (status)
);

CREATE TABLE role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (code)
);

CREATE TABLE namespace (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    description TEXT,
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_namespace_slug (slug),
    CONSTRAINT fk_namespace_created_by FOREIGN KEY (created_by) REFERENCES user_account(id)
);

CREATE TABLE local_credential (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_local_credential_user_id (user_id),
    UNIQUE KEY uk_local_credential_username (username),
    CONSTRAINT fk_local_credential_user_id FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE identity_binding (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    subject VARCHAR(256) NOT NULL,
    login_name VARCHAR(128),
    extra_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_identity_binding_provider_subject (provider_code, subject),
    KEY idx_identity_binding_user_id (user_id),
    CONSTRAINT fk_identity_binding_user_id FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE api_token (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    subject_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    name VARCHAR(64) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    scope_json TEXT NOT NULL,
    expires_at TIMESTAMP NULL,
    last_used_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_api_token_token_hash (token_hash),
    KEY idx_api_token_user_id (user_id),
    KEY idx_api_token_hash (token_hash),
    CONSTRAINT fk_api_token_user_id FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE user_role_binding (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role_binding_user_role (user_id, role_id),
    KEY idx_user_role_binding_user_id (user_id),
    CONSTRAINT fk_user_role_binding_user_id FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_user_role_binding_role_id FOREIGN KEY (role_id) REFERENCES role(id)
);

CREATE TABLE namespace_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    namespace_id BIGINT NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_namespace_member_namespace_user (namespace_id, user_id),
    KEY idx_namespace_member_user_id (user_id),
    KEY idx_namespace_member_namespace_id (namespace_id),
    CONSTRAINT fk_namespace_member_namespace_id FOREIGN KEY (namespace_id) REFERENCES namespace(id),
    CONSTRAINT fk_namespace_member_user_id FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE idempotency_record (
    request_id VARCHAR(255) PRIMARY KEY,
    resource_type VARCHAR(100),
    resource_id BIGINT,
    status VARCHAR(20) NOT NULL,
    response_status_code INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_idempotency_record_expires_at ON idempotency_record(expires_at);
CREATE INDEX idx_idempotency_record_status_created ON idempotency_record(status, created_at);

CREATE TABLE skill_storage_delete_compensation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    skill_id BIGINT,
    namespace VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    storage_keys_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    last_attempt_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_storage_delete_comp_status_created
    ON skill_storage_delete_compensation (status, created_at);
