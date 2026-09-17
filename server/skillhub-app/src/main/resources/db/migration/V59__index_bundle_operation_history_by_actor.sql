CREATE INDEX idx_suite_bundle_operation_actor_priority_updated
    ON skill_suite_bundle_operation(
        actor_id,
        (CASE
            WHEN status IN ('BLOCKED_RETRYABLE', 'REPREVIEW_REQUIRED') THEN 0
            WHEN status IN ('RUNNING', 'WAITING_FOR_MEMBERS') THEN 1
            ELSE 2
        END),
        updated_at DESC,
        operation_id DESC
    );
