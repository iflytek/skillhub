-- Encrypted Login Connection Secret versions. Cleartext never enters this schema.

CREATE TABLE login_connection_secret_version (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) REFERENCES organization(id),
    scope_key VARCHAR(64) GENERATED ALWAYS AS (
        COALESCE(organization_id, '@platform')
    ) STORED,
    connection_id VARCHAR(64) NOT NULL,
    purpose VARCHAR(128) NOT NULL,
    binding_version BIGINT NOT NULL,
    algorithm VARCHAR(32) NOT NULL,
    key_id VARCHAR(128) NOT NULL,
    nonce BYTEA NOT NULL,
    ciphertext BYTEA NOT NULL,
    status VARCHAR(32) NOT NULL,
    valid_until TIMESTAMPTZ,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by VARCHAR(128) REFERENCES user_account(id),
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_login_connection_secret_scope
        FOREIGN KEY (connection_id, scope_key)
        REFERENCES login_connection(id, scope_key),
    CONSTRAINT uk_login_connection_secret_binding
        UNIQUE (connection_id, binding_version),
    CONSTRAINT ck_login_connection_secret_purpose CHECK (
        purpose ~ '^[a-z][a-z0-9._-]{2,127}$'
    ),
    CONSTRAINT ck_login_connection_secret_binding_version CHECK (binding_version > 0),
    CONSTRAINT ck_login_connection_secret_algorithm CHECK (algorithm = 'AES-256-GCM'),
    CONSTRAINT ck_login_connection_secret_key_id CHECK (
        key_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$'
    ),
    CONSTRAINT ck_login_connection_secret_nonce CHECK (octet_length(nonce) = 12),
    CONSTRAINT ck_login_connection_secret_ciphertext CHECK (octet_length(ciphertext) > 16),
    CONSTRAINT ck_login_connection_secret_status CHECK (
        status IN ('CURRENT', 'RETIRING', 'REVOKED')
    ),
    CONSTRAINT ck_login_connection_secret_lifecycle CHECK (
        (status = 'CURRENT'
            AND valid_until IS NULL AND revoked_by IS NULL AND revoked_at IS NULL)
        OR (status = 'RETIRING'
            AND valid_until IS NOT NULL AND revoked_by IS NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_by IS NOT NULL AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_login_connection_secret_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_login_connection_secret_current
    ON login_connection_secret_version (connection_id, purpose)
    WHERE status = 'CURRENT';

CREATE UNIQUE INDEX uk_login_connection_secret_retiring
    ON login_connection_secret_version (connection_id, purpose)
    WHERE status = 'RETIRING';

CREATE INDEX idx_login_connection_secret_tenant_connection
    ON login_connection_secret_version (
        organization_id, connection_id, purpose, binding_version DESC
    );

CREATE INDEX idx_login_connection_secret_retiring_expiry
    ON login_connection_secret_version (valid_until, connection_id, purpose)
    WHERE status = 'RETIRING';

ALTER TABLE login_connection_revision
    ADD CONSTRAINT fk_login_connection_revision_secret_binding
        FOREIGN KEY (connection_id, secret_binding_version)
        REFERENCES login_connection_secret_version(connection_id, binding_version);
