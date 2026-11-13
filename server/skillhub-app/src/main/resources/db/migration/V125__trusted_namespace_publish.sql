-- Team-only automatic publication is a per-member grant, not a namespace switch.
ALTER TABLE namespace_member
    ADD COLUMN team_auto_publication_grant_id UUID;

ALTER TABLE review_task
    ADD COLUMN auto_publication_grant_id UUID,
    ADD COLUMN submission_may_submit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN auto_publication_cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN decision_method VARCHAR(32),
    ADD COLUMN decision_scan_task_id VARCHAR(100),
    ADD COLUMN manual_review_notified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_namespace_member_auto_publication_grant
    ON namespace_member(team_auto_publication_grant_id)
    WHERE team_auto_publication_grant_id IS NOT NULL;

CREATE INDEX idx_review_task_auto_publication_grant
    ON review_task(auto_publication_grant_id)
    WHERE auto_publication_grant_id IS NOT NULL;
