-- Consolidated Flyway initialization script
-- Generated from historical migrations V1..V39.
-- The archived per-version migration files are kept under db/migration-archive for reference only.


-- -----------------------------------------------------------------------------
-- BEGIN V1__init_schema.sql
-- -----------------------------------------------------------------------------

-- Phase 1 核心表：认证与授权

-- 用户账号表
CREATE TABLE user_account (
    id VARCHAR(128) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(256),
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    merged_to_user_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_account_email ON user_account(email);
CREATE INDEX idx_user_account_status ON user_account(status);

-- OAuth 身份绑定表
CREATE TABLE identity_binding (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    provider_code VARCHAR(64) NOT NULL,
    subject VARCHAR(256) NOT NULL,
    login_name VARCHAR(128),
    extra_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider_code, subject)
);

CREATE INDEX idx_identity_binding_user_id ON identity_binding(user_id);

-- API Token 表
CREATE TABLE api_token (
    id BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    subject_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    name VARCHAR(128) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    scope_json JSONB NOT NULL,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_token_user_id ON api_token(user_id);
CREATE INDEX idx_api_token_hash ON api_token(token_hash);

-- 角色表
CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 权限表
CREATE TABLE permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    group_code VARCHAR(64)
);

-- 角色权限关联表
CREATE TABLE role_permission (
    role_id BIGINT NOT NULL REFERENCES role(id),
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    PRIMARY KEY (role_id, permission_id)
);

-- 用户角色绑定表
CREATE TABLE user_role_binding (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    role_id BIGINT NOT NULL REFERENCES role(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, role_id)
);

CREATE INDEX idx_user_role_binding_user_id ON user_role_binding(user_id);

-- 命名空间表
CREATE TABLE namespace (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    description TEXT,
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 命名空间成员表
CREATE TABLE namespace_member (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(namespace_id, user_id)
);

CREATE INDEX idx_namespace_member_user_id ON namespace_member(user_id);
CREATE INDEX idx_namespace_member_namespace_id ON namespace_member(namespace_id);

-- 审计日志表
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id VARCHAR(128) REFERENCES user_account(id),
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id BIGINT,
    request_id VARCHAR(64),
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    detail_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_actor ON audit_log(actor_user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_log_request_id ON audit_log(request_id);

-- 插入系统内置角色
INSERT INTO role (code, name, description, is_system) VALUES
('SUPER_ADMIN', '超级管理员', '拥有所有权限', TRUE),
('SKILL_ADMIN', '技能管理员', '全局空间审核、提升审核、隐藏/撤回', TRUE),
('USER_ADMIN', '用户管理员', '准入审批、封禁/解封、角色分配', TRUE),
('AUDITOR', '审计员', '查看审计日志', TRUE);

-- 插入系统权限
INSERT INTO permission (code, name, group_code) VALUES
('skill:publish', '发布技能', 'skill'),
('skill:manage', '管理技能', 'skill'),
('skill:promote', '提升到全局', 'skill'),
('review:approve', '审核技能', 'review'),
('promotion:approve', '审核提升申请', 'promotion'),
('user:manage', '管理用户', 'user'),
('user:approve', '审批用户准入', 'user'),
('audit:read', '查看审计日志', 'audit');

-- 绑定角色权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.code = 'SKILL_ADMIN' AND p.code IN ('review:approve', 'skill:manage', 'promotion:approve');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.code = 'USER_ADMIN' AND p.code IN ('user:manage', 'user:approve');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.code = 'AUDITOR' AND p.code = 'audit:read';

-- 插入系统内置 @global 命名空间
INSERT INTO namespace (slug, display_name, type, description, status)
VALUES ('global', 'Global', 'GLOBAL', 'Platform-level public namespace', 'ACTIVE');


-- -----------------------------------------------------------------------------
-- END V1__init_schema.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V2__phase2_skill_tables.sql
-- -----------------------------------------------------------------------------

-- V2__phase2_skill_tables.sql
-- Phase 2: 命名空间 + Skill 核心链路

-- 技能主表
CREATE TABLE skill (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(128) NOT NULL,
    display_name VARCHAR(256),
    summary VARCHAR(512),
    owner_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    source_skill_id BIGINT,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    latest_version_id BIGINT,
    download_count BIGINT NOT NULL DEFAULT 0,
    star_count INT NOT NULL DEFAULT 0,
    rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128) REFERENCES user_account(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(namespace_id, slug)
);

CREATE INDEX idx_skill_namespace_status ON skill(namespace_id, status);

-- 技能版本表
CREATE TABLE skill_version (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    changelog TEXT,
    parsed_metadata_json JSONB,
    manifest_json JSONB,
    file_count INT NOT NULL DEFAULT 0,
    total_size BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, version)
);

CREATE INDEX idx_skill_version_skill_status ON skill_version(skill_id, status);

ALTER TABLE skill ADD CONSTRAINT fk_skill_latest_version
    FOREIGN KEY (latest_version_id) REFERENCES skill_version(id);

-- 技能文件表
CREATE TABLE skill_file (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES skill_version(id),
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(128),
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(version_id, file_path)
);

-- 技能标签表
CREATE TABLE skill_tag (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    tag_name VARCHAR(64) NOT NULL,
    version_id BIGINT NOT NULL REFERENCES skill_version(id),
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, tag_name)
);

-- 搜索文档表
CREATE TABLE skill_search_document (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL UNIQUE REFERENCES skill(id),
    namespace_id BIGINT NOT NULL,
    namespace_slug VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    title VARCHAR(256),
    summary VARCHAR(512),
    keywords VARCHAR(512),
    search_text TEXT,
    visibility VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);
CREATE INDEX idx_search_doc_namespace ON skill_search_document(namespace_id);
CREATE INDEX idx_search_doc_visibility ON skill_search_document(visibility);


-- -----------------------------------------------------------------------------
-- END V2__phase2_skill_tables.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V3__phase3_review_social_tables.sql
-- -----------------------------------------------------------------------------

-- V3__phase3_review_social_tables.sql
-- Phase 3: 审核工作流、提升、评分/收藏、幂等性

-- 审核任务表
CREATE TABLE review_task (
    id BIGSERIAL PRIMARY KEY,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id),
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version INT NOT NULL DEFAULT 1,
    submitted_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    reviewed_by VARCHAR(128) REFERENCES user_account(id),
    review_comment TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE INDEX idx_review_task_namespace_status ON review_task(namespace_id, status);
CREATE INDEX idx_review_task_submitted_by_status ON review_task(submitted_by, status);
CREATE UNIQUE INDEX idx_review_task_version_pending ON review_task(skill_version_id) WHERE status = 'PENDING';

-- 提升申请表
CREATE TABLE promotion_request (
    id BIGSERIAL PRIMARY KEY,
    source_skill_id BIGINT NOT NULL REFERENCES skill(id),
    source_version_id BIGINT NOT NULL REFERENCES skill_version(id),
    target_namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    target_skill_id BIGINT REFERENCES skill(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    version INT NOT NULL DEFAULT 1,
    submitted_by VARCHAR(128) NOT NULL REFERENCES user_account(id),
    reviewed_by VARCHAR(128) REFERENCES user_account(id),
    review_comment TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE INDEX idx_promotion_request_source_skill ON promotion_request(source_skill_id);
CREATE INDEX idx_promotion_request_status ON promotion_request(status);
CREATE UNIQUE INDEX idx_promotion_request_version_pending ON promotion_request(source_version_id) WHERE status = 'PENDING';

-- 技能收藏表
CREATE TABLE skill_star (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, user_id)
);

CREATE INDEX idx_skill_star_user_id ON skill_star(user_id);
CREATE INDEX idx_skill_star_skill_id ON skill_star(skill_id);

-- 技能评分表
CREATE TABLE skill_rating (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    score SMALLINT NOT NULL CHECK (score >= 1 AND score <= 5),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, user_id)
);

CREATE INDEX idx_skill_rating_skill_id ON skill_rating(skill_id);

-- 幂等性记录表
CREATE TABLE idempotency_record (
    request_id VARCHAR(64) PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT,
    status VARCHAR(32) NOT NULL,
    response_status_code INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_idempotency_record_expires_at ON idempotency_record(expires_at);
CREATE INDEX idx_idempotency_record_status_created ON idempotency_record(status, created_at);


-- -----------------------------------------------------------------------------
-- END V3__phase3_review_social_tables.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V4__normalize_skill_slugs.sql
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION skillhub_slugify(raw_text TEXT)
RETURNS VARCHAR(100)
LANGUAGE plpgsql
AS $$
DECLARE
    slug TEXT;
BEGIN
    IF raw_text IS NULL OR btrim(raw_text) = '' THEN
        RAISE EXCEPTION 'skill slug source cannot be blank';
    END IF;

    slug := lower(btrim(raw_text));
    slug := regexp_replace(slug, '[^a-z0-9]+', '-', 'g');
    slug := regexp_replace(slug, '^-+', '');
    slug := regexp_replace(slug, '-+$', '');
    slug := regexp_replace(slug, '-{2,}', '-', 'g');

    IF slug = '' THEN
        RAISE EXCEPTION 'skill slug normalization produced empty slug for input %', raw_text;
    END IF;

    IF length(slug) < 2 OR length(slug) > 64 THEN
        RAISE EXCEPTION 'normalized skill slug % has invalid length', slug;
    END IF;

    IF slug IN ('admin', 'api', 'dashboard', 'search', 'auth', 'me', 'global', 'system', 'static', 'assets', 'health') THEN
        RAISE EXCEPTION 'normalized skill slug % is reserved', slug;
    END IF;

    RETURN slug::VARCHAR(100);
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        WITH normalized AS (
            SELECT id, namespace_id, slug, skillhub_slugify(slug) AS normalized_slug
            FROM skill
        )
        SELECT 1
        FROM normalized
        GROUP BY namespace_id, normalized_slug
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'skill slug normalization would create duplicate slugs; resolve manually before applying migration';
    END IF;

    UPDATE skill
    SET slug = skillhub_slugify(slug)
    WHERE slug <> skillhub_slugify(slug);
END;
$$;

DROP FUNCTION skillhub_slugify(TEXT);


-- -----------------------------------------------------------------------------
-- END V4__normalize_skill_slugs.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V5__phase4_auth_governance.sql
-- -----------------------------------------------------------------------------

CREATE TABLE local_credential (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_local_credential_username ON local_credential (username);
CREATE UNIQUE INDEX idx_local_credential_user_id ON local_credential (user_id);

CREATE TABLE account_merge_request (
    id BIGSERIAL PRIMARY KEY,
    primary_user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    secondary_user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    verification_token VARCHAR(255),
    token_expires_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merge_primary_status ON account_merge_request (primary_user_id, status);
CREATE UNIQUE INDEX idx_merge_secondary_pending
    ON account_merge_request (secondary_user_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_merge_token_pending
    ON account_merge_request (verification_token)
    WHERE status = 'PENDING';


-- -----------------------------------------------------------------------------
-- END V5__phase4_auth_governance.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V6__phase4_governance_audit.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE skill ADD COLUMN hidden_at TIMESTAMP;
ALTER TABLE skill ADD COLUMN hidden_by VARCHAR(128) REFERENCES user_account(id);

ALTER TABLE skill_version ADD COLUMN yanked_at TIMESTAMP;
ALTER TABLE skill_version ADD COLUMN yanked_by VARCHAR(128) REFERENCES user_account(id);
ALTER TABLE skill_version ADD COLUMN yank_reason TEXT;

CREATE INDEX idx_skill_hidden ON skill(hidden) WHERE hidden = TRUE;
CREATE INDEX idx_audit_log_actor_time ON audit_log(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_action_time ON audit_log(action, created_at DESC);


-- -----------------------------------------------------------------------------
-- END V6__phase4_governance_audit.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V7__fix_audit_log_jsonb_type.sql
-- -----------------------------------------------------------------------------

-- Fix audit_log detail_json column to properly handle JSONB type
-- This migration ensures existing data is compatible with the JSONB type

-- The column is already defined as jsonb in V1, so this migration is a no-op
-- for fresh installations. For existing installations with text data, this would
-- have been needed, but since the column was always jsonb, we just verify it exists.

-- No-op migration: column is already jsonb in V1
-- This file exists to maintain migration version continuity
SELECT 1;


-- -----------------------------------------------------------------------------
-- END V7__fix_audit_log_jsonb_type.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V8__token_name_constraints.sql
-- -----------------------------------------------------------------------------

ALTER TABLE api_token
    ALTER COLUMN name TYPE VARCHAR(64);

CREATE UNIQUE INDEX uk_api_token_user_active_name
    ON api_token (user_id, LOWER(name))
    WHERE revoked_at IS NULL;


-- -----------------------------------------------------------------------------
-- END V8__token_name_constraints.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V9__expand_skill_summary_storage.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill
    ALTER COLUMN summary TYPE TEXT;

DROP INDEX IF EXISTS idx_search_vector;

ALTER TABLE skill_search_document
    DROP COLUMN search_vector;

ALTER TABLE skill_search_document
    ALTER COLUMN summary TYPE TEXT;

ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);


-- -----------------------------------------------------------------------------
-- END V9__expand_skill_summary_storage.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V10__skill_report_tables.sql
-- -----------------------------------------------------------------------------

CREATE TABLE skill_report (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id) ON DELETE CASCADE,
    reporter_id VARCHAR(128) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    details TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handled_by VARCHAR(128),
    handle_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP
);

CREATE INDEX idx_skill_report_status_created_at ON skill_report(status, created_at DESC);
CREATE INDEX idx_skill_report_skill_id ON skill_report(skill_id);


-- -----------------------------------------------------------------------------
-- END V10__skill_report_tables.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V11__skill_search_semantic_vector.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill_search_document
ADD COLUMN semantic_vector TEXT;


-- -----------------------------------------------------------------------------
-- END V11__skill_search_semantic_vector.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V12__governance_notifications.sql
-- -----------------------------------------------------------------------------

CREATE TABLE user_notification (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_user_notification_user_created_at ON user_notification(user_id, created_at DESC);
CREATE INDEX idx_user_notification_user_status ON user_notification(user_id, status, created_at DESC);


-- -----------------------------------------------------------------------------
-- END V12__governance_notifications.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V13__skill_owner_uniqueness.sql
-- -----------------------------------------------------------------------------

-- V12__skill_owner_uniqueness.sql
-- Change skill uniqueness from (namespace_id, slug) to (namespace_id, slug, owner_id)
-- to support owner-isolated skill records with the same name

ALTER TABLE skill DROP CONSTRAINT skill_namespace_id_slug_key;
ALTER TABLE skill ADD CONSTRAINT skill_namespace_id_slug_owner_id_key UNIQUE(namespace_id, slug, owner_id);


-- -----------------------------------------------------------------------------
-- END V13__skill_owner_uniqueness.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V14__add_skill_version_stats.sql
-- -----------------------------------------------------------------------------

CREATE TABLE skill_version_stats (
    skill_version_id BIGINT PRIMARY KEY REFERENCES skill_version(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    download_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_version_stats_skill_id ON skill_version_stats(skill_id);


-- -----------------------------------------------------------------------------
-- END V14__add_skill_version_stats.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V15__skill_version_download_state.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill_version
    ADD COLUMN bundle_ready BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN download_ready BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE skill_version
SET download_ready = CASE
        WHEN status = 'PUBLISHED' AND file_count > 0 THEN TRUE
        ELSE FALSE
    END,
    bundle_ready = FALSE;


-- -----------------------------------------------------------------------------
-- END V15__skill_version_download_state.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V16__skill_hidden_at_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill
    ALTER COLUMN hidden_at TYPE TIMESTAMPTZ USING hidden_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V16__skill_hidden_at_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V17__skill_created_updated_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V17__skill_created_updated_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V18__namespace_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE namespace
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE namespace_member
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V18__namespace_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V19__skill_secondary_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill_tag
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE skill_file
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE skill_version_stats
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V19__skill_secondary_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V20__social_and_skill_report_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill_star
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE skill_rating
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE skill_report
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN handled_at TYPE TIMESTAMPTZ USING handled_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V20__social_and_skill_report_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V21__user_account_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE user_account
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V21__user_account_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V22__auth_supporting_tables_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE identity_binding
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE role
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE user_role_binding
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE local_credential
    ALTER COLUMN locked_until TYPE TIMESTAMPTZ USING locked_until AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V22__auth_supporting_tables_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V23__review_and_idempotency_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE review_task
    ALTER COLUMN submitted_at TYPE TIMESTAMPTZ USING submitted_at AT TIME ZONE 'UTC',
    ALTER COLUMN reviewed_at TYPE TIMESTAMPTZ USING reviewed_at AT TIME ZONE 'UTC';

ALTER TABLE promotion_request
    ALTER COLUMN submitted_at TYPE TIMESTAMPTZ USING submitted_at AT TIME ZONE 'UTC',
    ALTER COLUMN reviewed_at TYPE TIMESTAMPTZ USING reviewed_at AT TIME ZONE 'UTC';

ALTER TABLE idempotency_record
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V23__review_and_idempotency_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V24__api_token_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE api_token
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_used_at TYPE TIMESTAMPTZ USING last_used_at AT TIME ZONE 'UTC',
    ALTER COLUMN revoked_at TYPE TIMESTAMPTZ USING revoked_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V24__api_token_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V25__account_merge_request_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE account_merge_request
    ALTER COLUMN token_expires_at TYPE TIMESTAMPTZ USING token_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V25__account_merge_request_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V26__skill_version_timestamptz.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill_version
    ALTER COLUMN published_at TYPE TIMESTAMPTZ USING published_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN yanked_at TYPE TIMESTAMPTZ USING yanked_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V26__skill_version_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V27__profile_change_request.sql
-- -----------------------------------------------------------------------------

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


-- -----------------------------------------------------------------------------
-- END V27__profile_change_request.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V28__optimize_skill_search_newest_order.sql
-- -----------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_skill_active_visible_updated
    ON skill (updated_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND hidden = FALSE;


-- -----------------------------------------------------------------------------
-- END V28__optimize_skill_search_newest_order.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V29__optimize_skill_search_popularity_order.sql
-- -----------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_skill_active_visible_downloads
    ON skill (download_count DESC, updated_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND hidden = FALSE;

CREATE INDEX IF NOT EXISTS idx_skill_active_visible_rating
    ON skill (rating_avg DESC, updated_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND hidden = FALSE;


-- -----------------------------------------------------------------------------
-- END V29__optimize_skill_search_popularity_order.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V30__expand_skill_search_keywords_storage.sql
-- -----------------------------------------------------------------------------

DROP INDEX IF EXISTS idx_search_vector;

ALTER TABLE skill_search_document
    DROP COLUMN search_vector;

ALTER TABLE skill_search_document
    ALTER COLUMN keywords TYPE TEXT;


-- -----------------------------------------------------------------------------
-- END V30__expand_skill_search_keywords_storage.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V31__expand_skill_search_title_storage.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill_search_document
    ALTER COLUMN title TYPE VARCHAR(512);

ALTER TABLE skill_search_document
ADD COLUMN search_vector tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce(summary, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords, '')), 'B') ||
    setweight(to_tsvector('simple', coalesce(search_text, '')), 'C')
) STORED;

CREATE INDEX idx_search_vector ON skill_search_document USING GIN (search_vector);


-- -----------------------------------------------------------------------------
-- END V31__expand_skill_search_title_storage.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V32__add_requested_visibility_to_skill_version.sql
-- -----------------------------------------------------------------------------

ALTER TABLE skill_version
    ADD COLUMN requested_visibility VARCHAR(20);


-- -----------------------------------------------------------------------------
-- END V32__add_requested_visibility_to_skill_version.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V33__skill_delete_storage_compensation.sql
-- -----------------------------------------------------------------------------

CREATE TABLE skill_storage_delete_compensation (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT,
    namespace VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    storage_keys_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skill_storage_delete_comp_status_created
    ON skill_storage_delete_compensation (status, created_at);


-- -----------------------------------------------------------------------------
-- END V33__skill_delete_storage_compensation.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V34__skill_label_system.sql
-- -----------------------------------------------------------------------------

-- V34__skill_label_system.sql

CREATE TABLE label_definition (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(64) UNIQUE NOT NULL,
    type VARCHAR(16) NOT NULL CHECK (type IN ('RECOMMENDED', 'PRIVILEGED')),
    visible_in_filter BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_label_definition_visible_sort ON label_definition(visible_in_filter, type, sort_order, id);

CREATE TABLE label_translation (
    id BIGSERIAL PRIMARY KEY,
    label_id BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    locale VARCHAR(16) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(label_id, locale)
);

CREATE INDEX idx_label_translation_label_id ON label_translation(label_id);

CREATE TABLE skill_label (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, label_id)
);

CREATE INDEX idx_skill_label_label_id ON skill_label(label_id);
CREATE INDEX idx_skill_label_skill_id ON skill_label(skill_id);


-- -----------------------------------------------------------------------------
-- END V34__skill_label_system.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V35__security_audit.sql
-- -----------------------------------------------------------------------------

-- Security audit table for tracking automated security scans
-- Supports multiple scanner types and multiple scan rounds per version
-- Uses soft delete to preserve audit history

CREATE TABLE security_audit (
    id BIGSERIAL PRIMARY KEY,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id),
    scan_id VARCHAR(100),
    scanner_type VARCHAR(50) NOT NULL DEFAULT 'skill-scanner',
    verdict VARCHAR(20) NOT NULL,
    is_safe BOOLEAN NOT NULL,
    max_severity VARCHAR(20),
    findings_count INT NOT NULL DEFAULT 0,
    findings JSONB NOT NULL DEFAULT '[]'::jsonb,
    scan_duration_seconds DOUBLE PRECISION,
    scanned_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL
);

-- Index for querying active audits by version
CREATE INDEX idx_security_audit_version_active
ON security_audit(skill_version_id, deleted_at)
WHERE deleted_at IS NULL;

-- Index for querying by verdict
CREATE INDEX idx_security_audit_verdict
ON security_audit(verdict);

-- Index for querying latest audit by version + scanner type
CREATE INDEX idx_security_audit_version_type_latest
ON security_audit(skill_version_id, scanner_type, created_at DESC)
WHERE deleted_at IS NULL;

-- Comments
COMMENT ON TABLE security_audit IS
'Audit records from automated security scanners. Supports multiple scanner types and multiple scan rounds per version. Uses soft delete (deleted_at) to preserve history.';

COMMENT ON COLUMN security_audit.scanner_type IS
'Type of scanner that performed the audit (e.g., skill-scanner). Extensible for future scanner integrations.';

COMMENT ON COLUMN security_audit.deleted_at IS
'Soft delete timestamp. NULL means active, non-NULL means logically deleted. Records are retained for audit trail even after skill_version is deleted.';


-- -----------------------------------------------------------------------------
-- END V35__security_audit.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V36__security_audit_timestamptz.sql
-- -----------------------------------------------------------------------------

-- Align security_audit timestamp columns with project convention (TIMESTAMPTZ)
-- Matches pattern established in V16–V26 for all other tables

ALTER TABLE security_audit
    ALTER COLUMN scanned_at TYPE TIMESTAMPTZ USING scanned_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';


-- -----------------------------------------------------------------------------
-- END V36__security_audit_timestamptz.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V37__notification_system.sql
-- -----------------------------------------------------------------------------

CREATE TABLE notification (
    id              BIGSERIAL PRIMARY KEY,
    recipient_id    VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body_json       TEXT,
    entity_type     VARCHAR(64),
    entity_id       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ
);

CREATE INDEX idx_notification_recipient_created ON notification(recipient_id, created_at DESC);
CREATE INDEX idx_notification_recipient_status ON notification(recipient_id, status, created_at DESC);

CREATE TABLE notification_preference (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE(user_id, category, channel)
);


-- -----------------------------------------------------------------------------
-- END V37__notification_system.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V38__drop_security_audit_skill_version_fk.sql
-- -----------------------------------------------------------------------------

-- Allow soft-deleted security audit history to survive skill version deletion.
-- The application stores skill_version_id as a plain identifier and intentionally
-- soft deletes security_audit rows before removing draft/rejected versions.

ALTER TABLE security_audit
    DROP CONSTRAINT IF EXISTS security_audit_skill_version_id_fkey;


-- -----------------------------------------------------------------------------
-- END V38__drop_security_audit_skill_version_fk.sql
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- BEGIN V39__password_reset_request.sql
-- -----------------------------------------------------------------------------

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


-- -----------------------------------------------------------------------------
-- END V39__password_reset_request.sql
-- -----------------------------------------------------------------------------
