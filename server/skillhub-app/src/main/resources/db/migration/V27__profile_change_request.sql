-- Profile change request table.
-- Tracks user-initiated profile modifications (display name, avatar, etc.)
-- with optional machine and human review workflow.
-- The 'changes' and 'old_values' columns use JSONB to support batch field updates
-- in a single request, e.g. {"displayName": "new name", "avatarUrl": "https://..."}

CREATE TABLE profile_change_request (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(128) NOT NULL REFERENCES user_account(id),
    changes         JSONB        NOT NULL,   -- requested field changes (key = field name, value = new value)
    old_values      JSONB,                   -- snapshot of previous values before this change
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
                                             -- PENDING | MACHINE_REJECTED | APPROVED | REJECTED | CANCELLED
    machine_result  VARCHAR(32),             -- PASS | FAIL | SKIPPED
    machine_reason  TEXT,                    -- rejection reason from machine review
    reviewer_id     VARCHAR(128) REFERENCES user_account(id),  -- human reviewer who acted on this request
    review_comment  TEXT,                    -- human reviewer's comment
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     TIMESTAMP               -- timestamp when human review was completed
);

CREATE INDEX idx_pcr_user_id    ON profile_change_request(user_id);
CREATE INDEX idx_pcr_status     ON profile_change_request(status);
CREATE INDEX idx_pcr_created    ON profile_change_request(created_at DESC);
CREATE INDEX idx_pcr_changes    ON profile_change_request USING GIN (changes);
