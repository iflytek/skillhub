CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body_json       TEXT,
    entity_type     VARCHAR(64),
    entity_id       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ
);

CREATE INDEX idx_notification_recipient_created ON notification(recipient_id, created_at DESC);
CREATE INDEX idx_notification_recipient_status ON notification(recipient_id, status, created_at DESC);

CREATE TABLE notification_preference (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE(user_id, category, channel)
);
