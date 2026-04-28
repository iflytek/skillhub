ALTER TABLE api_token
    ALTER COLUMN name TYPE VARCHAR(64);

CREATE UNIQUE INDEX uk_api_token_user_active_name
    ON api_token (user_id, LOWER(name))
    WHERE revoked_at IS NULL;
