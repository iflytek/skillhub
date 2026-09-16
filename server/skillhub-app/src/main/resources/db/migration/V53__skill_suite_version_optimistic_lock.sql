-- Reject stale draft edits after another transaction publishes or otherwise changes the snapshot.
ALTER TABLE skill_suite_version
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
