-- Media asset table backing GIF / image attachments for skills, bundles, and promotions.
-- Card lists default to a static cover; detail screens load the full asset.

CREATE TABLE media_asset (
    id            BIGSERIAL PRIMARY KEY,
    owner_type    VARCHAR(64)  NOT NULL,
    owner_id      BIGINT       NOT NULL,
    media_type    VARCHAR(32)  NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    file_path     VARCHAR(512),
    object_key    VARCHAR(512) NOT NULL,
    content_type  VARCHAR(128) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    alt_text      VARCHAR(256),
    sort_order    INT          NOT NULL DEFAULT 0,
    created_by    VARCHAR(128) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_asset_owner   ON media_asset (owner_type, owner_id);
CREATE INDEX idx_media_asset_sha256  ON media_asset (sha256);
CREATE INDEX idx_media_asset_role    ON media_asset (owner_type, owner_id, role, sort_order);
