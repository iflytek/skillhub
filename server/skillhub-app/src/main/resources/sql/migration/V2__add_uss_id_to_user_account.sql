ALTER TABLE user_account
    ADD COLUMN uss_id VARCHAR(128);

CREATE UNIQUE INDEX idx_user_account_uss_id ON user_account(uss_id);
