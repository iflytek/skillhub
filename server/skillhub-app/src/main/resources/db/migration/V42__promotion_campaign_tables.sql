-- Promotion management tables for operational promotion (slots/campaigns/events).
-- Distinct from existing promotion_request which represents global elevation governance.

CREATE TABLE promotion_slot (
    id              BIGSERIAL PRIMARY KEY,
    slot_code       VARCHAR(64)  NOT NULL UNIQUE,
    display_name    VARCHAR(128) NOT NULL,
    target_types    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    max_active_items INT         NOT NULL DEFAULT 5,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE promotion_campaign (
    id                BIGSERIAL PRIMARY KEY,
    target_type       VARCHAR(32)  NOT NULL,
    target_id         BIGINT       NOT NULL,
    target_version_id BIGINT,
    slot_code         VARCHAR(64)  NOT NULL,
    title             VARCHAR(128) NOT NULL,
    subtitle          VARCHAR(512),
    cover_media_id    BIGINT,
    demo_media_id     BIGINT,
    priority          INT          NOT NULL DEFAULT 50,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    starts_at         TIMESTAMPTZ  NOT NULL,
    ends_at           TIMESTAMPTZ  NOT NULL,
    submitted_by      VARCHAR(128) NOT NULL,
    reviewed_by       VARCHAR(128),
    review_comment    TEXT,
    reason            TEXT,
    version           INT          NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT promotion_campaign_time_window_chk CHECK (ends_at > starts_at)
);

CREATE INDEX idx_promotion_campaign_slot_status_window
    ON promotion_campaign (slot_code, status, starts_at, ends_at, priority);
CREATE INDEX idx_promotion_campaign_target
    ON promotion_campaign (target_type, target_id);
CREATE INDEX idx_promotion_campaign_status_created
    ON promotion_campaign (status, created_at);

CREATE TABLE promotion_event_log (
    id            BIGSERIAL PRIMARY KEY,
    campaign_id   BIGINT       NOT NULL,
    event_type    VARCHAR(32)  NOT NULL,
    user_id       VARCHAR(128),
    anonymous_id  VARCHAR(128),
    request_id    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promotion_event_campaign      ON promotion_event_log (campaign_id);
CREATE INDEX idx_promotion_event_type_created  ON promotion_event_log (event_type, created_at);
CREATE INDEX idx_promotion_event_user          ON promotion_event_log (user_id);

INSERT INTO promotion_slot (slot_code, display_name, target_types, max_active_items, enabled) VALUES
    ('HOME_HERO',              '首页首屏',         '["SKILL","SKILL_BUNDLE"]', 5,  TRUE),
    ('HOME_FEATURED_SKILLS',   '首页精选技能',     '["SKILL"]',                12, TRUE),
    ('HOME_FEATURED_BUNDLES',  '首页精选技能包',   '["SKILL_BUNDLE"]',         8,  TRUE),
    ('SEARCH_PINNED',          '搜索结果置顶',     '["SKILL","SKILL_BUNDLE"]', 6,  TRUE),
    ('CATEGORY_FEATURED',      '分类页精选',       '["SKILL","SKILL_BUNDLE"]', 6,  TRUE),
    ('DETAIL_RELATED',         '详情页相关推荐',   '["SKILL","SKILL_BUNDLE"]', 8,  TRUE),
    ('CLI_RECOMMENDED',        'CLI 推荐位',       '["SKILL","SKILL_BUNDLE"]', 12, TRUE);
