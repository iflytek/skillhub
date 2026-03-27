-- Password reset request table for self-service and admin-triggered password recovery
CREATE TABLE password_reset_request (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    requested_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
    requested_by_user_id BIGINT REFERENCES user_account(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_request_user_id ON password_reset_request(user_id);
CREATE INDEX idx_password_reset_request_expires_at ON password_reset_request(expires_at);

COMMENT ON TABLE password_reset_request IS 'Stores password reset requests with hashed tokens for local account recovery';
COMMENT ON COLUMN password_reset_request.token_hash IS 'BCrypt hash of the one-time reset token';
COMMENT ON COLUMN password_reset_request.requested_by_admin IS 'True if triggered by admin, false if self-service';
COMMENT ON COLUMN password_reset_request.requested_by_user_id IS 'Admin user who triggered the reset, if applicable';
