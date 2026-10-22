CREATE TABLE notification_email_delivery (
 id BIGSERIAL PRIMARY KEY,
 recipient_id VARCHAR(128) NOT NULL,
 email_revision BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 window_key VARCHAR(200) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 attempts INTEGER NOT NULL DEFAULT 0,
 due_at TIMESTAMPTZ NOT NULL,
 next_attempt_at TIMESTAMPTZ NOT NULL,
 lease_token VARCHAR(36), lease_until TIMESTAMPTZ,
 result_code VARCHAR(48), accepted_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 is_recovery_root BOOLEAN NOT NULL DEFAULT FALSE,
 merged_into_id BIGINT REFERENCES notification_email_delivery(id) ON DELETE CASCADE,
 recovery_cutoff_at TIMESTAMPTZ, source_high_watermark BIGINT NOT NULL DEFAULT 0,
 source_cursor BIGINT NOT NULL DEFAULT 0, sources_complete BOOLEAN NOT NULL DEFAULT FALSE,
 item_cursor BIGINT NOT NULL DEFAULT 0, items_complete BOOLEAN NOT NULL DEFAULT FALSE,
 preview_item_ids VARCHAR(1200) NOT NULL DEFAULT '', item_count BIGINT NOT NULL DEFAULT 0,
 retry_started_at TIMESTAMPTZ, retry_deadline TIMESTAMPTZ, terminal_at TIMESTAMPTZ,
 UNIQUE(recipient_id, window_key)
);
CREATE INDEX idx_notification_email_due ON notification_email_delivery(status,next_attempt_at,due_at);
CREATE INDEX idx_notification_email_lease ON notification_email_delivery(status,lease_until);
CREATE TABLE notification_email_item (
 id BIGSERIAL PRIMARY KEY,
 delivery_id BIGINT NOT NULL REFERENCES notification_email_delivery(id) ON DELETE CASCADE,
 recipient_id VARCHAR(128) NOT NULL,
 event_key VARCHAR(200) NOT NULL,
 category VARCHAR(32) NOT NULL,
 event_type VARCHAR(64) NOT NULL,
 skill_id BIGINT, entity_id BIGINT,
 occurred_at TIMESTAMPTZ NOT NULL, enqueued_at TIMESTAMPTZ NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
 UNIQUE(recipient_id,event_key)
);
CREATE INDEX idx_notification_email_item_batch ON notification_email_item(delivery_id,id);

CREATE INDEX idx_notification_email_recipient ON notification_email_delivery(recipient_id,id);
CREATE INDEX idx_notification_email_parent ON notification_email_delivery(merged_into_id,id);
CREATE INDEX idx_notification_email_user_work ON notification_email_delivery(recipient_id,kind,status,id);

CREATE TABLE notification_email_cancel_barrier (
 scope_key VARCHAR(256) PRIMARY KEY,
 recipient_id VARCHAR(128), category VARCHAR(32),
 through_item_id BIGINT NOT NULL, cursor_id BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_email_item_recipient_category ON notification_email_item(recipient_id,category,id);
CREATE INDEX idx_notification_email_cancel_pending ON notification_email_cancel_barrier(updated_at,scope_key)
 WHERE cursor_id<through_item_id;
CREATE INDEX idx_notification_email_suppression_sweep ON notification_email_delivery(status,items_complete,terminal_at,id);
