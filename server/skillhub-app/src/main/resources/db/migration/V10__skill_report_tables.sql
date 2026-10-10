CREATE TABLE skill_report (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id) ON DELETE CASCADE,
    reporter_id VARCHAR(128) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    details TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handled_by VARCHAR(128),
    handle_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP
);

CREATE INDEX idx_skill_report_status_created_at ON skill_report(status, created_at DESC);
CREATE INDEX idx_skill_report_skill_id ON skill_report(skill_id);
