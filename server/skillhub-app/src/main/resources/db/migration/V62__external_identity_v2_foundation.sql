-- External Identity V2 foundation. This migration only expands the schema; it does not migrate,
-- update, or remove the legacy identity_binding read path.

CREATE TABLE login_connection (
    id VARCHAR(64) PRIMARY KEY,
    public_handle VARCHAR(128) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    organization_id VARCHAR(64) REFERENCES organization(id),
    scope_key VARCHAR(64) GENERATED ALWAYS AS (
        COALESCE(organization_id, '@platform')
    ) STORED,
    system_key VARCHAR(128),
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    adapter_key VARCHAR(128) NOT NULL,
    active_revision_id VARCHAR(64),
    last_tested_revision_id VARCHAR(64),
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_login_connection_public_handle UNIQUE (public_handle),
    CONSTRAINT uk_login_connection_id_scope UNIQUE (id, scope_key),
    CONSTRAINT uk_login_connection_organization_id UNIQUE (organization_id, id),
    CONSTRAINT ck_login_connection_scope CHECK (
        (scope_type = 'PLATFORM' AND organization_id IS NULL)
        OR (scope_type = 'ORGANIZATION' AND organization_id IS NOT NULL)
    ),
    CONSTRAINT ck_login_connection_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'DISABLED')
    ),
    CONSTRAINT ck_login_connection_adapter_key CHECK (length(btrim(adapter_key)) > 0),
    CONSTRAINT ck_login_connection_public_handle CHECK (
        public_handle ~ '^[A-Za-z0-9][A-Za-z0-9_-]{7,127}$'
    ),
    CONSTRAINT ck_login_connection_system_key CHECK (
        system_key IS NULL OR length(btrim(system_key)) > 0
    ),
    CONSTRAINT ck_login_connection_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_login_connection_scoped_system_key
    ON login_connection (scope_key, system_key)
    WHERE system_key IS NOT NULL;

CREATE INDEX idx_login_connection_tenant_status
    ON login_connection (organization_id, status, id)
    WHERE organization_id IS NOT NULL;

CREATE INDEX idx_login_connection_adapter_status
    ON login_connection (adapter_key, status, id);

CREATE TABLE login_connection_revision (
    id VARCHAR(64) PRIMARY KEY,
    connection_id VARCHAR(64) NOT NULL REFERENCES login_connection(id),
    revision BIGINT NOT NULL,
    adapter_contract_version VARCHAR(32) NOT NULL,
    config_schema_version INTEGER NOT NULL,
    capabilities JSONB NOT NULL,
    typed_config JSONB NOT NULL,
    secret_binding_version BIGINT,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_login_connection_revision_number UNIQUE (connection_id, revision),
    CONSTRAINT uk_login_connection_revision_identity UNIQUE (connection_id, id),
    CONSTRAINT ck_login_connection_revision_positive CHECK (revision > 0),
    CONSTRAINT ck_login_connection_revision_contract CHECK (
        adapter_contract_version ~ '^[1-9][0-9]*\.[0-9]+$'
    ),
    CONSTRAINT ck_login_connection_revision_schema CHECK (config_schema_version > 0),
    CONSTRAINT ck_login_connection_revision_capabilities CHECK (
        jsonb_typeof(capabilities) = 'array'
    ),
    CONSTRAINT ck_login_connection_revision_config CHECK (
        jsonb_typeof(typed_config) = 'object'
    ),
    CONSTRAINT ck_login_connection_revision_secret_version CHECK (
        secret_binding_version IS NULL OR secret_binding_version >= 0
    )
);

ALTER TABLE login_connection
    ADD CONSTRAINT fk_login_connection_active_revision
        FOREIGN KEY (id, active_revision_id)
        REFERENCES login_connection_revision(connection_id, id),
    ADD CONSTRAINT fk_login_connection_last_tested_revision
        FOREIGN KEY (id, last_tested_revision_id)
        REFERENCES login_connection_revision(connection_id, id);

CREATE INDEX idx_login_connection_revision_created
    ON login_connection_revision (connection_id, revision DESC, id);

CREATE TABLE external_identity (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) REFERENCES organization(id),
    scope_key VARCHAR(64) GENERATED ALWAYS AS (
        COALESCE(organization_id, '@platform')
    ) STORED,
    connection_id VARCHAR(64) NOT NULL,
    issuer VARCHAR(512) NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_value VARCHAR(512) NOT NULL,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_authenticated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_external_identity_connection_scope
        FOREIGN KEY (connection_id, scope_key)
        REFERENCES login_connection(id, scope_key),
    CONSTRAINT uk_external_identity_coordinate UNIQUE (
        connection_id, issuer, subject_type, subject_value
    ),
    CONSTRAINT uk_external_identity_tenant_id UNIQUE (organization_id, id),
    CONSTRAINT uk_external_identity_tenant_id_user UNIQUE (organization_id, id, user_id),
    CONSTRAINT ck_external_identity_issuer CHECK (length(btrim(issuer)) > 0),
    CONSTRAINT ck_external_identity_subject_type CHECK (length(btrim(subject_type)) > 0),
    CONSTRAINT ck_external_identity_subject_value CHECK (length(btrim(subject_value)) > 0),
    CONSTRAINT ck_external_identity_status CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')
    ),
    CONSTRAINT ck_external_identity_version CHECK (version >= 0)
);

CREATE INDEX idx_external_identity_tenant_user
    ON external_identity (organization_id, user_id, status, id)
    WHERE organization_id IS NOT NULL;

CREATE INDEX idx_external_identity_connection_status
    ON external_identity (connection_id, status, id);

CREATE INDEX idx_external_identity_user_status
    ON external_identity (user_id, status, id);

CREATE UNIQUE INDEX uk_organization_membership_tenant_id_user
    ON organization_membership (organization_id, id, user_id);

CREATE TABLE identity_session_origin (
    id VARCHAR(64) PRIMARY KEY,
    session_key_hash CHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    organization_id VARCHAR(64) NOT NULL REFERENCES organization(id),
    membership_id VARCHAR(64) NOT NULL,
    login_connection_id VARCHAR(64) NOT NULL,
    external_identity_id VARCHAR(64) NOT NULL,
    authenticated_at TIMESTAMPTZ NOT NULL,
    assurance VARCHAR(64) NOT NULL,
    organization_authority_version BIGINT NOT NULL,
    membership_authority_version BIGINT NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_identity_session_origin_hash UNIQUE (session_key_hash),
    CONSTRAINT fk_identity_session_membership
        FOREIGN KEY (organization_id, membership_id, user_id)
        REFERENCES organization_membership(organization_id, id, user_id),
    CONSTRAINT fk_identity_session_connection
        FOREIGN KEY (organization_id, login_connection_id)
        REFERENCES login_connection(organization_id, id),
    CONSTRAINT fk_identity_session_external_identity
        FOREIGN KEY (organization_id, external_identity_id, user_id)
        REFERENCES external_identity(organization_id, id, user_id),
    CONSTRAINT ck_identity_session_hash CHECK (
        session_key_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_identity_session_assurance CHECK (length(btrim(assurance)) > 0),
    CONSTRAINT ck_identity_session_authority_versions CHECK (
        organization_authority_version >= 0 AND membership_authority_version >= 0
    ),
    CONSTRAINT ck_identity_session_version CHECK (version >= 0)
);

CREATE INDEX idx_identity_session_tenant_user
    ON identity_session_origin (organization_id, user_id, revoked_at, id);

CREATE INDEX idx_identity_session_membership_active
    ON identity_session_origin (organization_id, membership_id, authenticated_at DESC, id)
    WHERE revoked_at IS NULL;

CREATE TABLE identity_operation (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) REFERENCES organization(id),
    scope_key VARCHAR(64) GENERATED ALWAYS AS (
        COALESCE(organization_id, '@platform')
    ) STORED,
    connection_id VARCHAR(64),
    operation_type VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    request_id VARCHAR(128),
    external_request_id VARCHAR(256),
    source_type VARCHAR(128),
    source_reference VARCHAR(256),
    error_code VARCHAR(128),
    error_summary VARCHAR(512),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_identity_operation_connection_scope
        FOREIGN KEY (connection_id, scope_key)
        REFERENCES login_connection(id, scope_key),
    CONSTRAINT uk_identity_operation_id_scope UNIQUE (id, scope_key),
    CONSTRAINT ck_identity_operation_type CHECK (length(btrim(operation_type)) > 0),
    CONSTRAINT ck_identity_operation_source CHECK (
        (source_type IS NULL AND source_reference IS NULL)
        OR (source_type IS NOT NULL AND length(btrim(source_type)) > 0
            AND source_reference IS NOT NULL AND length(btrim(source_reference)) > 0)
    ),
    CONSTRAINT ck_identity_operation_status CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CONFLICT')
    ),
    CONSTRAINT ck_identity_operation_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status <> 'PENDING' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_identity_operation_version CHECK (version >= 0)
);

CREATE INDEX idx_identity_operation_tenant_status
    ON identity_operation (organization_id, status, started_at DESC, id)
    WHERE organization_id IS NOT NULL;

CREATE INDEX idx_identity_operation_request
    ON identity_operation (request_id, started_at DESC, id)
    WHERE request_id IS NOT NULL;

CREATE INDEX idx_identity_operation_connection_external
    ON identity_operation (connection_id, external_request_id, id)
    WHERE connection_id IS NOT NULL AND external_request_id IS NOT NULL;

CREATE TABLE identity_conflict (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) REFERENCES organization(id),
    scope_key VARCHAR(64) GENERATED ALWAYS AS (
        COALESCE(organization_id, '@platform')
    ) STORED,
    operation_id VARCHAR(64),
    connection_id VARCHAR(64),
    conflict_type VARCHAR(128) NOT NULL,
    subject_reference VARCHAR(256) NOT NULL,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    safe_summary VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    resolution_code VARCHAR(128),
    resolved_by VARCHAR(128) REFERENCES user_account(id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_identity_conflict_operation_scope
        FOREIGN KEY (operation_id, scope_key)
        REFERENCES identity_operation(id, scope_key),
    CONSTRAINT fk_identity_conflict_connection_scope
        FOREIGN KEY (connection_id, scope_key)
        REFERENCES login_connection(id, scope_key),
    CONSTRAINT ck_identity_conflict_type CHECK (length(btrim(conflict_type)) > 0),
    CONSTRAINT ck_identity_conflict_subject CHECK (length(btrim(subject_reference)) > 0),
    CONSTRAINT ck_identity_conflict_candidates CHECK (candidate_count >= 0),
    CONSTRAINT ck_identity_conflict_status CHECK (
        status IN ('OPEN', 'RESOLVED', 'DISMISSED')
    ),
    CONSTRAINT ck_identity_conflict_resolution CHECK (
        (status = 'OPEN' AND resolution_code IS NULL AND resolved_by IS NULL AND resolved_at IS NULL)
        OR (status <> 'OPEN' AND resolution_code IS NOT NULL
            AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
    ),
    CONSTRAINT ck_identity_conflict_version CHECK (version >= 0)
);

CREATE INDEX idx_identity_conflict_tenant_status
    ON identity_conflict (organization_id, status, created_at DESC, id)
    WHERE organization_id IS NOT NULL;

CREATE INDEX idx_identity_conflict_connection_status
    ON identity_conflict (connection_id, status, created_at DESC, id)
    WHERE connection_id IS NOT NULL;
