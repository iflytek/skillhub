CREATE INDEX idx_suite_bundle_operation_actor_active_updated
    ON skill_suite_bundle_operation(actor_id, updated_at DESC, operation_id DESC)
    WHERE status IN ('RUNNING', 'WAITING_FOR_MEMBERS', 'BLOCKED_RETRYABLE');
