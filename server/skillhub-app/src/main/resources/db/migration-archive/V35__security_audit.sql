-- Security audit table for tracking automated security scans
-- Supports multiple scanner types and multiple scan rounds per version
-- Uses soft delete to preserve audit history

CREATE TABLE security_audit (
    id BIGSERIAL PRIMARY KEY,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id),
    scan_id VARCHAR(100),
    scanner_type VARCHAR(50) NOT NULL DEFAULT 'skill-scanner',
    verdict VARCHAR(20) NOT NULL,
    is_safe BOOLEAN NOT NULL,
    max_severity VARCHAR(20),
    findings_count INT NOT NULL DEFAULT 0,
    findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    scan_duration_seconds DOUBLE PRECISION,
    scanned_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Index for querying active audits by version
CREATE INDEX idx_security_audit_version_active
ON security_audit(skill_version_id, deleted_at)
WHERE deleted_at IS NULL;

-- Index for querying by verdict
CREATE INDEX idx_security_audit_verdict
ON security_audit(verdict);

-- Index for querying latest audit by version + scanner type
CREATE INDEX idx_security_audit_version_type_latest
ON security_audit(skill_version_id, scanner_type, created_at DESC)
WHERE deleted_at IS NULL;

-- Comments
COMMENT ON TABLE security_audit IS
'Audit records from automated security scanners. Supports multiple scanner types and multiple scan rounds per version. Uses soft delete (deleted_at) to preserve history.';

COMMENT ON COLUMN security_audit.scanner_type IS
'Type of scanner that performed the audit (e.g., skill-scanner). Extensible for future scanner integrations.';

COMMENT ON COLUMN security_audit.deleted_at IS
'Soft delete timestamp. NULL means active, non-NULL means logically deleted. Records are retained for audit trail even after skill_version is deleted.';
