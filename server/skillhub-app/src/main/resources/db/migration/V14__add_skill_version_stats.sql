CREATE TABLE skill_version_stats (
    skill_version_id BIGINT PRIMARY KEY REFERENCES skill_version(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    download_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_version_stats_skill_id ON skill_version_stats(skill_id);
