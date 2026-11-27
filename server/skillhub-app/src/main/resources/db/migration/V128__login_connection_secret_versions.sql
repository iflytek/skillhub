-- Encrypted credentials for organization login connections. Existing connections and revisions are preserved.
CREATE TABLE login_connection_secret_version (
    connection_id VARCHAR(64) NOT NULL,
    binding_version BIGINT NOT NULL,
    organization_id VARCHAR(64) NOT NULL,
    purpose VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    key_id VARCHAR(128) NOT NULL,
    nonce BYTEA NOT NULL,
    ciphertext BYTEA NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (connection_id, binding_version),
    CONSTRAINT fk_login_connection_secret_organization
        FOREIGN KEY (organization_id, connection_id)
        REFERENCES login_connection(organization_id, id),
    CONSTRAINT ck_login_connection_secret_binding_version CHECK (binding_version > 0),
    CONSTRAINT ck_login_connection_secret_envelope CHECK (
        octet_length(nonce) = 12 AND octet_length(ciphertext) > 16
    )
);

ALTER TABLE login_connection_revision
    ADD CONSTRAINT fk_login_connection_revision_secret_binding
        FOREIGN KEY (connection_id, secret_binding_version)
        REFERENCES login_connection_secret_version(connection_id, binding_version);
