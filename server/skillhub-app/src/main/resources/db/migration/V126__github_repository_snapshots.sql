-- Preserve per-path acquisition evidence before repository sync history is pruned.
CREATE TABLE github_repository_snapshot (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES github_source_binding(id) ON DELETE CASCADE,
    acquisition_task_id UUID NOT NULL UNIQUE,
    commit_sha CHAR(40) NOT NULL,
    ignore_patterns JSONB NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_github_repository_snapshot_source
    ON github_repository_snapshot (source_id, captured_at DESC, id DESC);

CREATE TABLE github_repository_snapshot_member (
    snapshot_id BIGINT NOT NULL REFERENCES github_repository_snapshot(id) ON DELETE CASCADE,
    skill_path VARCHAR(1024) NOT NULL,
    ingestion_id UUID NOT NULL REFERENCES github_sync_package_ingestion(id),
    PRIMARY KEY (snapshot_id, skill_path)
);

ALTER TABLE github_sync_package_ingestion
    ADD COLUMN reused_version_ingestion_id UUID REFERENCES github_sync_package_ingestion(id);

CREATE INDEX idx_github_ingestion_reused_version
    ON github_sync_package_ingestion (reused_version_ingestion_id)
    WHERE reused_version_ingestion_id IS NOT NULL;
