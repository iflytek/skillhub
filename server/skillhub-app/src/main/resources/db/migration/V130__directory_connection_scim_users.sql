-- P1 inbound SCIM directory connections and source-owned user profile fields.
-- Secrets are one-way hashed; the raw bootstrap/rotation value is returned once only.

ALTER TABLE organization_membership
    ADD COLUMN source_user_name VARCHAR(256);

CREATE TABLE directory_connection (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL REFERENCES organization(id),
    protocol VARCHAR(32) NOT NULL DEFAULT 'SCIM_2_0',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    secret_hash VARCHAR(64) NOT NULL,
    secret_version BIGINT NOT NULL DEFAULT 1,
    token_hint VARCHAR(16) NOT NULL,
    authority_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_directory_connection_organization_id UNIQUE (organization_id, id),
    CONSTRAINT ck_directory_connection_protocol CHECK (protocol = 'SCIM_2_0'),
    CONSTRAINT ck_directory_connection_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'DISABLED')
    ),
    CONSTRAINT ck_directory_connection_secret_hash CHECK (secret_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_directory_connection_secret_version CHECK (secret_version > 0),
    CONSTRAINT ck_directory_connection_token_hint CHECK (length(btrim(token_hint)) BETWEEN 4 AND 16),
    CONSTRAINT ck_directory_connection_authority_version CHECK (authority_version >= 0),
    CONSTRAINT ck_directory_connection_version CHECK (version >= 0)
);

CREATE INDEX idx_directory_connection_organization_status
    ON directory_connection (organization_id, status, id);

CREATE INDEX idx_directory_membership_source_user_name
    ON organization_membership (organization_id, source_type, source_user_name, id);
