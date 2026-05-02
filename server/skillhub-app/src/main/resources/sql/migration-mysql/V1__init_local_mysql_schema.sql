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

CREATE TABLE skill (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    namespace_id BIGINT NOT NULL,
    slug VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    summary TEXT,
    owner_id VARCHAR(128) NOT NULL,
    source_skill_id BIGINT,
    visibility VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latest_version_id BIGINT,
    download_count BIGINT NOT NULL DEFAULT 0,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_at TIMESTAMP NULL,
    hidden_by VARCHAR(128),
    star_count INT NOT NULL DEFAULT 0,
    rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_namespace_slug (namespace_id, slug),
    KEY idx_skill_namespace_status (namespace_id, status),
    CONSTRAINT fk_skill_namespace_id FOREIGN KEY (namespace_id) REFERENCES namespace(id),
    CONSTRAINT fk_skill_owner_id FOREIGN KEY (owner_id) REFERENCES user_account(id),
    CONSTRAINT fk_skill_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    CONSTRAINT fk_skill_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id),
    CONSTRAINT fk_skill_hidden_by FOREIGN KEY (hidden_by) REFERENCES user_account(id)
);

CREATE TABLE skill_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    changelog TEXT,
    parsed_metadata_json TEXT NULL,
    manifest_json TEXT NULL,
    requested_visibility VARCHAR(20),
    file_count INT NOT NULL DEFAULT 0,
    total_size BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP NULL,
    bundle_ready BOOLEAN NOT NULL DEFAULT FALSE,
    download_ready BOOLEAN NOT NULL DEFAULT FALSE,
    yanked_at TIMESTAMP NULL,
    yanked_by VARCHAR(128),
    yank_reason TEXT,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_version_skill_version (skill_id, version),
    KEY idx_skill_version_skill_status (skill_id, status),
    CONSTRAINT fk_skill_version_skill_id FOREIGN KEY (skill_id) REFERENCES skill(id),
    CONSTRAINT fk_skill_version_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    CONSTRAINT fk_skill_version_yanked_by FOREIGN KEY (yanked_by) REFERENCES user_account(id)
);

ALTER TABLE skill
    ADD CONSTRAINT fk_skill_latest_version
        FOREIGN KEY (latest_version_id) REFERENCES skill_version(id);

CREATE TABLE skill_version_stats (
    skill_version_id BIGINT NOT NULL PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    download_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_skill_version_stats_skill_id (skill_id),
    CONSTRAINT fk_skill_version_stats_skill_version_id FOREIGN KEY (skill_version_id) REFERENCES skill_version(id),
    CONSTRAINT fk_skill_version_stats_skill_id FOREIGN KEY (skill_id) REFERENCES skill(id)
);

CREATE TABLE profile_change_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    changes TEXT NOT NULL,
    old_values TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    machine_result VARCHAR(32) NULL,
    machine_reason TEXT NULL,
    reviewer_id VARCHAR(128) NULL,
    review_comment TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    KEY idx_profile_change_request_user_status (user_id, status),
    KEY idx_profile_change_request_status_created (status, created_at),
    CONSTRAINT fk_profile_change_request_user_id FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id VARCHAR(128) NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id BIGINT NULL,
    request_id VARCHAR(64) NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    detail_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_log_actor_user_id (actor_user_id),
    KEY idx_audit_log_action_created_at (action, created_at)
);

CREATE TABLE security_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    skill_version_id BIGINT NOT NULL,
    scan_id VARCHAR(100) NULL,
    scanner_type VARCHAR(50) NOT NULL,
    verdict VARCHAR(20) NOT NULL,
    is_safe BOOLEAN NOT NULL,
    max_severity VARCHAR(20) NULL,
    findings_count INT NOT NULL DEFAULT 0,
    findings TEXT NULL,
    scan_duration_seconds DOUBLE NULL,
    scanned_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    UNIQUE KEY uk_security_audit_scan_id (scan_id),
    KEY idx_security_audit_skill_version_created (skill_version_id, created_at),
    KEY idx_security_audit_skill_version_scanner_deleted (skill_version_id, scanner_type, deleted_at),
    CONSTRAINT fk_security_audit_skill_version_id FOREIGN KEY (skill_version_id) REFERENCES skill_version(id)
);
