-- Skill Suites are typed collections of exact published Skill versions.
-- They intentionally use separate tables so a Skill and Suite may share one namespace/slug.

CREATE TABLE skill_suite (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    summary TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    latest_version_id BIGINT,
    install_request_count BIGINT NOT NULL DEFAULT 0,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_at TIMESTAMPTZ,
    hidden_by VARCHAR(128),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_suite_namespace_slug UNIQUE (namespace_id, slug)
);

CREATE INDEX idx_skill_suite_namespace_status
    ON skill_suite(namespace_id, status);

CREATE TABLE skill_suite_version (
    id BIGSERIAL PRIMARY KEY,
    suite_id BIGINT NOT NULL REFERENCES skill_suite(id) ON DELETE CASCADE,
    version VARCHAR(64) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    summary TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    visibility VARCHAR(32) NOT NULL,
    changelog TEXT,
    published_at TIMESTAMPTZ,
    yanked_at TIMESTAMPTZ,
    yanked_by VARCHAR(128),
    yank_reason TEXT,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_suite_version UNIQUE (suite_id, version)
);

CREATE INDEX idx_skill_suite_version_suite_status
    ON skill_suite_version(suite_id, status);

CREATE TABLE skill_suite_version_member (
    id BIGSERIAL PRIMARY KEY,
    suite_version_id BIGINT NOT NULL REFERENCES skill_suite_version(id) ON DELETE CASCADE,
    skill_id BIGINT REFERENCES skill(id) ON DELETE SET NULL,
    skill_version_id BIGINT REFERENCES skill_version(id) ON DELETE SET NULL,
    position INT NOT NULL CHECK (position >= 0),
    entry BOOLEAN NOT NULL DEFAULT FALSE,
    namespace_slug_snapshot VARCHAR(128) NOT NULL,
    skill_slug_snapshot VARCHAR(128) NOT NULL,
    skill_version_snapshot VARCHAR(64) NOT NULL,
    fingerprint_snapshot VARCHAR(255) NOT NULL,
    CONSTRAINT uk_skill_suite_member_position UNIQUE (suite_version_id, position)
);

-- The partial index continues to allow multiple tombstoned historical rows after hard deletion.
CREATE UNIQUE INDEX uk_skill_suite_member_skill
    ON skill_suite_version_member(suite_version_id, skill_id)
    WHERE skill_id IS NOT NULL;

-- Entry is a role of one exact member. Keeping it on the snapshot row preserves the role even
-- when governance hard-deletes the referenced SkillVersion and clears its foreign keys.
CREATE UNIQUE INDEX uk_skill_suite_member_entry
    ON skill_suite_version_member(suite_version_id)
    WHERE entry = TRUE;

CREATE INDEX idx_skill_suite_member_entry_skill
    ON skill_suite_version_member(skill_id)
    WHERE entry = TRUE AND skill_id IS NOT NULL;

CREATE INDEX idx_skill_suite_member_version
    ON skill_suite_version_member(skill_version_id);

ALTER TABLE skill_suite
    ADD CONSTRAINT fk_skill_suite_latest_version
    FOREIGN KEY (latest_version_id) REFERENCES skill_suite_version(id) ON DELETE SET NULL;
