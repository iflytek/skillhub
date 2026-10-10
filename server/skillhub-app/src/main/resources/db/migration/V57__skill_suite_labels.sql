CREATE TABLE skill_suite_label (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL REFERENCES skill_suite(id) ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (suite_id, label_id)
);

CREATE INDEX idx_skill_suite_label_suite_id ON skill_suite_label(suite_id);
CREATE INDEX idx_skill_suite_label_label_id ON skill_suite_label(label_id);
