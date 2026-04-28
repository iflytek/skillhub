CREATE TABLE user_notification (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_user_notification_user_created_at ON user_notification(user_id, created_at DESC);
CREATE INDEX idx_user_notification_user_status ON user_notification(user_id, status, created_at DESC);
