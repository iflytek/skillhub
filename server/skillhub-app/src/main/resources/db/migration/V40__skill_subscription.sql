-- Skill subscription: users subscribe to skills for update notifications
CREATE TABLE skill_subscription (
    id          BIGSERIAL    PRIMARY KEY,
    skill_id    BIGINT       NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    user_id     VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_skill_subscription UNIQUE (skill_id, user_id)
);

CREATE INDEX idx_skill_subscription_user ON skill_subscription(user_id, created_at DESC);
CREATE INDEX idx_skill_subscription_skill ON skill_subscription(skill_id);

-- Add subscription_count column to skill table
ALTER TABLE skill ADD COLUMN subscription_count INTEGER NOT NULL DEFAULT 0;
