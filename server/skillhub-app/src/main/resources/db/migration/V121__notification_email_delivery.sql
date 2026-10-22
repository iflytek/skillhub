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
