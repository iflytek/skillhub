ALTER TABLE skill ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE skill ADD COLUMN hidden_at TIMESTAMP;
ALTER TABLE skill ADD COLUMN hidden_by VARCHAR(128) REFERENCES user_account(id);

ALTER TABLE skill_version ADD COLUMN yanked_at TIMESTAMP;
ALTER TABLE skill_version ADD COLUMN yanked_by VARCHAR(128) REFERENCES user_account(id);
ALTER TABLE skill_version ADD COLUMN yank_reason TEXT;

CREATE INDEX idx_skill_hidden ON skill(hidden) WHERE hidden = TRUE;
CREATE INDEX idx_audit_log_actor_time ON audit_log(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_action_time ON audit_log(action, created_at DESC);
