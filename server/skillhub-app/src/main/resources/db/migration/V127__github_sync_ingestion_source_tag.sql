-- Read-only upstream tag provenance captured with each GitHub sync ingestion.
-- NULL means the ingestion predates source tag recording or came from an older worker.
ALTER TABLE github_sync_package_ingestion
    ADD COLUMN source_tag_json JSONB,
    ADD CONSTRAINT ck_github_sync_ingestion_source_tag_json
        CHECK (source_tag_json IS NULL OR jsonb_typeof(source_tag_json) = 'object');
