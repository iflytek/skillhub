CREATE TABLE security_audit (
    id                    BIGSERIAL PRIMARY KEY,
    skill_version_id      BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    scan_id               VARCHAR(100),
    scanner_type          VARCHAR(50) NOT NULL DEFAULT 'skill-scanner',
    verdict               VARCHAR(20) NOT NULL,
    is_safe               BOOLEAN NOT NULL,
    max_severity          VARCHAR(20),
    findings_count        INT NOT NULL DEFAULT 0,
    findings              JSONB NOT NULL DEFAULT '[]'::jsonb,
    scan_duration_seconds DOUBLE PRECISION,
    scanned_at            TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_audit_version ON security_audit(skill_version_id);
CREATE INDEX idx_security_audit_verdict ON security_audit(verdict);
