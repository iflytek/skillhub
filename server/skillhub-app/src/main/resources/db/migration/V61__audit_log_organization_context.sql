-- Add organization context to the shared audit log so identity operations stay attributable
-- with opaque IDs and organization scoping.
ALTER TABLE audit_log
    ADD COLUMN organization_id VARCHAR(64) REFERENCES organization(id),
    ADD COLUMN target_ref VARCHAR(128),
    ADD COLUMN result VARCHAR(32);

ALTER TABLE audit_log
    ADD CONSTRAINT chk_audit_log_result
        CHECK (result IS NULL OR result IN ('SUCCESS', 'DENIED', 'FAILED'));

CREATE INDEX idx_audit_log_organization_created
    ON audit_log(organization_id, created_at DESC, id DESC);

CREATE INDEX idx_audit_log_target_ref
    ON audit_log(target_type, target_ref)
    WHERE target_ref IS NOT NULL;
