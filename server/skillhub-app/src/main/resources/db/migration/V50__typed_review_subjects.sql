-- Keep legacy Skill columns during the compatibility window while adding one typed review identity.
ALTER TABLE review_task
    ADD COLUMN subject_type VARCHAR(32),
    ADD COLUMN subject_id BIGINT,
    ADD COLUMN subject_version_id BIGINT,
    ADD COLUMN subject_version VARCHAR(64);

UPDATE review_task
SET subject_type = 'SKILL_VERSION',
    subject_id = skill_id,
    subject_version_id = skill_version_id,
    subject_version = skill_version;

-- During a rolling deployment, an older Server still inserts only the legacy Skill columns.
-- Populate the typed identity in PostgreSQL before NOT NULL validation so existing Skill review
-- writes remain compatible until every Server instance understands typed review subjects.
CREATE OR REPLACE FUNCTION populate_review_task_subject_from_legacy()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.subject_type IS NULL
            AND NEW.skill_id IS NOT NULL
            AND NEW.skill_version IS NOT NULL THEN
        NEW.subject_type := 'SKILL_VERSION';
        NEW.subject_id := NEW.skill_id;
        NEW.subject_version_id := NEW.skill_version_id;
        NEW.subject_version := NEW.skill_version;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_task_legacy_subject
    BEFORE INSERT ON review_task
    FOR EACH ROW
    EXECUTE FUNCTION populate_review_task_subject_from_legacy();

ALTER TABLE review_task
    ALTER COLUMN subject_type SET NOT NULL,
    ALTER COLUMN subject_id SET NOT NULL,
    ALTER COLUMN subject_version SET NOT NULL,
    ALTER COLUMN skill_id DROP NOT NULL,
    ALTER COLUMN skill_version DROP NOT NULL;

CREATE INDEX idx_review_task_subject_attempts
    ON review_task(subject_type, subject_id, subject_version, submitted_at DESC, id DESC);

CREATE UNIQUE INDEX idx_review_task_suite_version_pending
    ON review_task(subject_type, subject_version_id)
    WHERE subject_type = 'SUITE_VERSION' AND status = 'PENDING';
