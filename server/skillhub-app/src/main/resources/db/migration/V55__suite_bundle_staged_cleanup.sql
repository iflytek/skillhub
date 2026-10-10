ALTER TABLE skill_suite_bundle_preview
    ADD COLUMN staged_objects_cleaned_at TIMESTAMPTZ,
    ADD COLUMN staged_cleanup_failed_at TIMESTAMPTZ,
    ADD COLUMN staged_cleanup_failure_code VARCHAR(128);

ALTER TABLE skill_suite_bundle_operation
    ADD COLUMN staged_objects_cleaned_at TIMESTAMPTZ,
    ADD COLUMN staged_cleanup_failed_at TIMESTAMPTZ,
    ADD COLUMN staged_cleanup_failure_code VARCHAR(128);

CREATE INDEX idx_suite_bundle_preview_cleanup
    ON skill_suite_bundle_preview(status, expires_at)
    WHERE staged_objects_cleaned_at IS NULL AND status = 'EXPIRED';

CREATE INDEX idx_suite_bundle_operation_cleanup
    ON skill_suite_bundle_operation(status, completed_at)
    WHERE staged_objects_cleaned_at IS NULL
      AND status IN ('REPREVIEW_REQUIRED', 'SUITE_DRAFT_CREATED', 'CANCELLED');
