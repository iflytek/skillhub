ALTER TABLE skill_rating
    ADD COLUMN review_text VARCHAR(2000),
    ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN moderated_by VARCHAR(128) REFERENCES user_account(id),
    ADD COLUMN moderated_at TIMESTAMPTZ,
    ADD COLUMN moderation_reason VARCHAR(500),
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_skill_rating_review_status
        CHECK (review_status IN ('VISIBLE', 'HIDDEN'));

CREATE INDEX idx_skill_rating_visible_reviews
    ON skill_rating(skill_id, updated_at DESC, id DESC)
    WHERE review_status = 'VISIBLE'
      AND review_text IS NOT NULL
      AND BTRIM(review_text) <> '';
