-- V2__phase2_skill_tables.sql
-- Phase 2: 命名空间 + Skill 核心链路

-- 技能主表
CREATE TABLE skill (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(128) NOT NULL,
    display_name VARCHAR(256),
    summary VARCHAR(512),
    owner_id BIGINT NOT NULL REFERENCES user_account(id),
    source_skill_id BIGINT,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    latest_version_id BIGINT,
    download_count BIGINT NOT NULL DEFAULT 0,
    star_count INT NOT NULL DEFAULT 0,
    rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    rating_count INT NOT NULL DEFAULT 0,
    created_by BIGINT REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT REFERENCES user_account(id),
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
    created_by BIGINT REFERENCES user_account(id),
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
    created_by BIGINT REFERENCES user_account(id),
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
    owner_id BIGINT NOT NULL,
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
