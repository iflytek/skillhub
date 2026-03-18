ALTER TABLE skill_version
    ADD COLUMN bundle_ready BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN download_ready BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE skill_version
SET download_ready = CASE
        WHEN status = 'PUBLISHED' AND file_count > 0 THEN TRUE
        ELSE FALSE
    END,
    bundle_ready = FALSE;
