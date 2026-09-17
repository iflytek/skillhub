-- Tenant- and Login Connection-scoped immutable subject claims used to correlate a verified
-- authentication assertion with a pre-provisioned Organization Membership. The immutable login
-- subject is intentionally stored separately from Membership source metadata.

-- The protocol-neutral SubjectRef contract permits 1024 characters. Align the existing V2
-- binding column before the first production write path starts using it.
ALTER TABLE external_identity
    ALTER COLUMN subject_value TYPE VARCHAR(1024);

-- PostgreSQL requires the referenced column set itself to be unique for the composite tenant
-- foreign key below. Membership id remains the global primary key as well.
ALTER TABLE organization_membership
    ADD CONSTRAINT uk_organization_membership_tenant_id
        UNIQUE (organization_id, id);

CREATE TABLE preprovisioned_login_subject (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL REFERENCES organization(id),
    membership_id VARCHAR(64) NOT NULL,
    login_connection_id VARCHAR(64) NOT NULL,
    issuer VARCHAR(512) NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_value VARCHAR(1024) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_preprovisioned_subject_membership
        FOREIGN KEY (organization_id, membership_id)
        REFERENCES organization_membership(organization_id, id),
    CONSTRAINT fk_preprovisioned_subject_connection
        FOREIGN KEY (organization_id, login_connection_id)
        REFERENCES login_connection(organization_id, id),
    CONSTRAINT uk_preprovisioned_subject_coordinate UNIQUE (
        login_connection_id, issuer, subject_type, subject_value
    ),
    CONSTRAINT ck_preprovisioned_subject_issuer CHECK (length(btrim(issuer)) > 0),
    CONSTRAINT ck_preprovisioned_subject_type CHECK (length(btrim(subject_type)) > 0),
    CONSTRAINT ck_preprovisioned_subject_value CHECK (length(btrim(subject_value)) > 0),
    CONSTRAINT ck_preprovisioned_subject_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_preprovisioned_subject_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_preprovisioned_subject_version CHECK (version >= 0)
);

CREATE INDEX idx_preprovisioned_subject_tenant_membership
    ON preprovisioned_login_subject (organization_id, membership_id, status, id);

CREATE INDEX idx_preprovisioned_subject_connection_status
    ON preprovisioned_login_subject (login_connection_id, status, id);
