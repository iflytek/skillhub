-- Organization foundation shared by unified identity, enterprise SSO and later team features.
-- Expand-only: existing platform and Namespace tables remain untouched so the capability can be
-- rolled out behind feature flags.

CREATE TABLE organization (
    id VARCHAR(64) PRIMARY KEY,
    slug VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    authority_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_organization_slug UNIQUE (slug),
    CONSTRAINT ck_organization_slug_normalized CHECK (
        length(slug) BETWEEN 2 AND 64
        AND slug = lower(btrim(slug))
        AND slug !~ '[^a-z0-9-]'
        AND slug NOT LIKE '-%'
        AND slug NOT LIKE '%-'
        AND slug NOT LIKE '%--%'
    ),
    CONSTRAINT ck_organization_status CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'DECOMMISSIONED')
    ),
    CONSTRAINT ck_organization_authority_version CHECK (authority_version >= 0),
    CONSTRAINT ck_organization_version CHECK (version >= 0)
);

CREATE INDEX idx_organization_status_id
    ON organization (status, id);

CREATE TABLE organization_domain (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL REFERENCES organization(id),
    domain VARCHAR(253) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    verification_method VARCHAR(64),
    verification_token_hash VARCHAR(255),
    verified_at TIMESTAMPTZ,
    last_checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_organization_domain_claim UNIQUE (organization_id, domain),
    CONSTRAINT ck_organization_domain_normalized CHECK (
        length(domain) BETWEEN 1 AND 253
        AND domain = lower(btrim(domain))
    ),
    CONSTRAINT ck_organization_domain_status CHECK (
        status IN ('PENDING', 'VERIFIED', 'DISABLED')
    ),
    CONSTRAINT ck_organization_domain_verified_at CHECK (
        status <> 'VERIFIED' OR verified_at IS NOT NULL
    ),
    CONSTRAINT ck_organization_domain_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_organization_domain_verified_owner
    ON organization_domain (domain)
    WHERE status = 'VERIFIED';

CREATE INDEX idx_organization_domain_tenant_status
    ON organization_domain (organization_id, status, domain, id);

CREATE TABLE organization_membership (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL REFERENCES organization(id),
    user_id VARCHAR(128) REFERENCES user_account(id),
    status VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(256),
    display_name VARCHAR(128),
    primary_email VARCHAR(256),
    department VARCHAR(256),
    employee_number VARCHAR(128),
    authority_version BIGINT NOT NULL DEFAULT 0,
    activated_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    deprovisioned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_organization_membership_status CHECK (
        status IN ('INVITED', 'PROVISIONED', 'ACTIVE', 'SUSPENDED', 'DEPROVISIONED')
    ),
    CONSTRAINT ck_organization_membership_source_type CHECK (
        length(btrim(source_type)) > 0
    ),
    CONSTRAINT ck_organization_membership_authority_version CHECK (authority_version >= 0),
    CONSTRAINT ck_organization_membership_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_organization_membership_current_user
    ON organization_membership (organization_id, user_id)
    WHERE user_id IS NOT NULL AND status <> 'DEPROVISIONED';

CREATE UNIQUE INDEX uk_organization_membership_current_source
    ON organization_membership (organization_id, source_type, source_id)
    WHERE source_id IS NOT NULL AND status <> 'DEPROVISIONED';

CREATE INDEX idx_organization_membership_tenant_status
    ON organization_membership (organization_id, status, id);

CREATE INDEX idx_organization_membership_tenant_user
    ON organization_membership (organization_id, user_id, status, id);

CREATE INDEX idx_organization_membership_tenant_source
    ON organization_membership (organization_id, source_type, source_id, id);

CREATE TABLE organization_role_binding (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL REFERENCES organization(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    revoked_by VARCHAR(128) REFERENCES user_account(id),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_organization_role_binding_role CHECK (
        role IN (
            'ORG_OWNER',
            'IDENTITY_ADMIN',
            'LOGIN_SECRET_ADMIN',
            'MEMBER_ADMIN',
            'ORG_AUDITOR'
        )
    ),
    CONSTRAINT ck_organization_role_binding_status CHECK (
        status IN ('ACTIVE', 'REVOKED')
    ),
    CONSTRAINT ck_organization_role_binding_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL AND revoked_by IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_organization_role_binding_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_organization_role_binding_active
    ON organization_role_binding (organization_id, user_id, role)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_organization_role_binding_tenant_user
    ON organization_role_binding (organization_id, user_id, status, role, id);

CREATE INDEX idx_organization_role_binding_tenant_role
    ON organization_role_binding (organization_id, role, status, user_id, id);
