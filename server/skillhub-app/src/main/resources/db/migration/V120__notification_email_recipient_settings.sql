ALTER TABLE user_account ADD COLUMN email_revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE notification_email_contact (
 user_id VARCHAR(128) PRIMARY KEY REFERENCES user_account(id),
 verified_email VARCHAR(256), verified_revision BIGINT, verified_at TIMESTAMPTZ, account_enabled_since TIMESTAMPTZ,
 challenge_hash VARCHAR(256), challenge_id VARCHAR(36), challenge_email VARCHAR(256), challenge_revision BIGINT,
 challenge_expires_at TIMESTAMPTZ, failed_attempts INTEGER NOT NULL DEFAULT 0,
 resend_available_at TIMESTAMPTZ, request_window_start TIMESTAMPTZ, request_count INTEGER NOT NULL DEFAULT 0,
 locale VARCHAR(8) NOT NULL DEFAULT 'en', version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE notification_email_preference (
 id BIGSERIAL PRIMARY KEY, user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
 category VARCHAR(32) NOT NULL, mode VARCHAR(16) NOT NULL DEFAULT 'OFF', enabled_since TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(user_id,category)
);
