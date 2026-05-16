-- Skill bundle management tables: bundle aggregate, version, item snapshot,
-- validation results, review tasks, social interactions, search index, media assets.

CREATE TABLE skill_bundle (
    id                 BIGSERIAL PRIMARY KEY,
    namespace_id       BIGINT       NOT NULL,
    slug               VARCHAR(128) NOT NULL,
    display_name       VARCHAR(256) NOT NULL,
    summary            VARCHAR(512) NOT NULL,
    bundle_type        VARCHAR(32)  NOT NULL,
    owner_id           VARCHAR(128) NOT NULL,
    visibility         VARCHAR(32)  NOT NULL DEFAULT 'NAMESPACE_ONLY',
    status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    latest_version_id  BIGINT,
    download_count     BIGINT       NOT NULL DEFAULT 0,
    star_count         INT          NOT NULL DEFAULT 0,
    rating_avg         NUMERIC(3,2),
    rating_count       INT          NOT NULL DEFAULT 0,
    comment_count      INT          NOT NULL DEFAULT 0,
    created_by         VARCHAR(128) NOT NULL,
    updated_by         VARCHAR(128) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT skill_bundle_namespace_slug_unique UNIQUE (namespace_id, slug)
);
CREATE INDEX idx_skill_bundle_owner       ON skill_bundle (owner_id);
CREATE INDEX idx_skill_bundle_type        ON skill_bundle (bundle_type);
CREATE INDEX idx_skill_bundle_visibility  ON skill_bundle (visibility);
CREATE INDEX idx_skill_bundle_status      ON skill_bundle (status);

CREATE TABLE skill_bundle_version (
    id                  BIGSERIAL PRIMARY KEY,
    bundle_id           BIGINT       NOT NULL REFERENCES skill_bundle(id) ON DELETE CASCADE,
    version             VARCHAR(32)  NOT NULL,
    version_sort        BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    manifest_json       JSONB        NOT NULL,
    lock_json           JSONB        NOT NULL,
    bundle_storage_key  VARCHAR(512) NOT NULL,
    file_count          INT          NOT NULL DEFAULT 0,
    total_size          BIGINT       NOT NULL DEFAULT 0,
    validation_status   VARCHAR(32)  NOT NULL DEFAULT 'SCANNING',
    reject_reason       VARCHAR(512),
    published_by        VARCHAR(128),
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT skill_bundle_version_bundle_version_unique UNIQUE (bundle_id, version)
);
CREATE INDEX idx_skill_bundle_version_status     ON skill_bundle_version (status);
CREATE INDEX idx_skill_bundle_version_bundle     ON skill_bundle_version (bundle_id, version_sort);
CREATE INDEX idx_skill_bundle_version_validation ON skill_bundle_version (validation_status);

CREATE TABLE skill_bundle_item (
    id                  BIGSERIAL PRIMARY KEY,
    bundle_version_id   BIGINT       NOT NULL REFERENCES skill_bundle_version(id) ON DELETE CASCADE,
    source_type         VARCHAR(32)  NOT NULL,
    skill_id            BIGINT,
    skill_version_id    BIGINT,
    embedded_skill_key  VARCHAR(512),
    namespace_slug      VARCHAR(128) NOT NULL,
    skill_slug          VARCHAR(128) NOT NULL,
    version             VARCHAR(32)  NOT NULL,
    display_name        VARCHAR(256) NOT NULL,
    summary             VARCHAR(512),
    role_description    VARCHAR(512) NOT NULL,
    required            BOOLEAN      NOT NULL DEFAULT TRUE,
    install_order       INT          NOT NULL DEFAULT 0,
    compatibility_json  JSONB
);
CREATE INDEX idx_skill_bundle_item_version  ON skill_bundle_item (bundle_version_id);
CREATE INDEX idx_skill_bundle_item_skill    ON skill_bundle_item (skill_id);
CREATE INDEX idx_skill_bundle_item_skillver ON skill_bundle_item (skill_version_id);

CREATE TABLE skill_bundle_validation_result (
    id                BIGSERIAL PRIMARY KEY,
    bundle_version_id BIGINT       NOT NULL REFERENCES skill_bundle_version(id) ON DELETE CASCADE,
    bundle_item_id    BIGINT       REFERENCES skill_bundle_item(id) ON DELETE CASCADE,
    check_type        VARCHAR(64)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    severity          VARCHAR(32),
    message           TEXT,
    related_audit_id  BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_skill_bundle_validation_version ON skill_bundle_validation_result (bundle_version_id);
CREATE INDEX idx_skill_bundle_validation_item    ON skill_bundle_validation_result (bundle_item_id);
CREATE INDEX idx_skill_bundle_validation_status  ON skill_bundle_validation_result (status);

CREATE TABLE skill_bundle_review_task (
    id                BIGSERIAL PRIMARY KEY,
    bundle_version_id BIGINT       NOT NULL REFERENCES skill_bundle_version(id) ON DELETE CASCADE,
    namespace_id      BIGINT       NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    version           INT          NOT NULL DEFAULT 1,
    submitted_by      VARCHAR(128) NOT NULL,
    reviewed_by       VARCHAR(128),
    review_comment    TEXT,
    submitted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_at       TIMESTAMPTZ,
    CONSTRAINT skill_bundle_review_task_version_unique UNIQUE (bundle_version_id)
);
CREATE INDEX idx_skill_bundle_review_status     ON skill_bundle_review_task (status);
CREATE INDEX idx_skill_bundle_review_namespace  ON skill_bundle_review_task (namespace_id);
CREATE INDEX idx_skill_bundle_review_submitter  ON skill_bundle_review_task (submitted_by);

CREATE TABLE skill_bundle_star (
    bundle_id   BIGINT       NOT NULL REFERENCES skill_bundle(id) ON DELETE CASCADE,
    user_id     VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (bundle_id, user_id)
);

CREATE TABLE skill_bundle_rating (
    bundle_id   BIGINT       NOT NULL REFERENCES skill_bundle(id) ON DELETE CASCADE,
    user_id     VARCHAR(128) NOT NULL,
    score       SMALLINT     NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (bundle_id, user_id)
);

CREATE TABLE skill_bundle_comment (
    id          BIGSERIAL PRIMARY KEY,
    bundle_id   BIGINT       NOT NULL REFERENCES skill_bundle(id) ON DELETE CASCADE,
    user_id     VARCHAR(128) NOT NULL,
    content     TEXT         NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'VISIBLE',
    hidden_by   VARCHAR(128),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_skill_bundle_comment_bundle ON skill_bundle_comment (bundle_id);
CREATE INDEX idx_skill_bundle_comment_status ON skill_bundle_comment (status);

CREATE TABLE skill_bundle_download_event (
    id                 BIGSERIAL PRIMARY KEY,
    bundle_id          BIGINT       NOT NULL REFERENCES skill_bundle(id) ON DELETE CASCADE,
    bundle_version_id  BIGINT       NOT NULL,
    user_id            VARCHAR(128),
    client_ip          VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_skill_bundle_download_event_bundle  ON skill_bundle_download_event (bundle_id);
CREATE INDEX idx_skill_bundle_download_event_created ON skill_bundle_download_event (created_at);

CREATE TABLE skill_bundle_search_document (
    id                    BIGSERIAL PRIMARY KEY,
    bundle_id             BIGINT       NOT NULL UNIQUE REFERENCES skill_bundle(id) ON DELETE CASCADE,
    namespace_id          BIGINT       NOT NULL,
    owner_id              VARCHAR(128) NOT NULL,
    title                 VARCHAR(256) NOT NULL,
    summary               VARCHAR(512) NOT NULL,
    bundle_type           VARCHAR(32)  NOT NULL,
    target_project_types  VARCHAR(512),
    role_tags             VARCHAR(512),
    item_skill_text       TEXT,
    search_text           TEXT         NOT NULL,
    visibility            VARCHAR(32)  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_skill_bundle_search_namespace  ON skill_bundle_search_document (namespace_id);
CREATE INDEX idx_skill_bundle_search_type       ON skill_bundle_search_document (bundle_type);
CREATE INDEX idx_skill_bundle_search_visibility ON skill_bundle_search_document (visibility);
CREATE INDEX idx_skill_bundle_search_text       ON skill_bundle_search_document USING GIN (to_tsvector('simple', search_text));
