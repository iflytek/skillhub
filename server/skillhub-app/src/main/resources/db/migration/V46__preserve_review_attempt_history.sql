ALTER TABLE review_task
    ADD COLUMN skill_id BIGINT,
    ADD COLUMN skill_version VARCHAR(64);

UPDATE review_task task
SET skill_id = version.skill_id,
    skill_version = version.version
FROM skill_version version
WHERE task.skill_version_id = version.id;

ALTER TABLE review_task
    ALTER COLUMN skill_id SET NOT NULL,
    ALTER COLUMN skill_version SET NOT NULL,
    ALTER COLUMN skill_version_id DROP NOT NULL;

ALTER TABLE review_task
    DROP CONSTRAINT review_task_skill_version_id_fkey,
    ADD CONSTRAINT fk_review_task_skill_version
        FOREIGN KEY (skill_version_id) REFERENCES skill_version(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_review_task_skill
        FOREIGN KEY (skill_id) REFERENCES skill(id);

CREATE INDEX idx_review_task_submitter_submitted
    ON review_task(submitted_by, submitted_at DESC, id DESC);

CREATE INDEX idx_review_task_skill_version_attempts
    ON review_task(skill_id, skill_version, submitted_at DESC, id DESC);
