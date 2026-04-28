CREATE INDEX IF NOT EXISTS idx_skill_active_visible_downloads
    ON skill (download_count DESC, updated_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND hidden = FALSE;

CREATE INDEX IF NOT EXISTS idx_skill_active_visible_rating
    ON skill (rating_avg DESC, updated_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND hidden = FALSE;
