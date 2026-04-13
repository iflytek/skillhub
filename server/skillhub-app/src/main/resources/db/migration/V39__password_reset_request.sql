-- Password reset verification code records for self-service and admin-triggered flows
CREATE TABLE password_reset_request (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    requested_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
    requested_by_user_id VARCHAR(128) REFERENCES user_account(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_request_user_id ON password_reset_request(user_id);
CREATE INDEX idx_password_reset_request_expires_at ON password_reset_request(expires_at);

COMMENT ON TABLE password_reset_request IS 'Stores password reset verification code requests for local account recovery';
COMMENT ON COLUMN password_reset_request.code_hash IS 'BCrypt hash of the one-time verification code';
COMMENT ON COLUMN password_reset_request.requested_by_admin IS 'True when the reset is triggered by an administrator';
COMMENT ON COLUMN password_reset_request.requested_by_user_id IS 'Admin user who triggered the reset, if applicable';
