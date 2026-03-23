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
