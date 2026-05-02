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
