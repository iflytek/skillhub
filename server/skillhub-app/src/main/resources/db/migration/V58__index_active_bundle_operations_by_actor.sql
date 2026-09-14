CREATE INDEX idx_suite_bundle_operation_actor_status_updated
    ON skill_suite_bundle_operation(actor_id, status, updated_at DESC, operation_id DESC);
