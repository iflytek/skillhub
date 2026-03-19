CREATE INDEX IF NOT EXISTS idx_skill_active_visible_updated
    ON skill (updated_at DESC, id DESC)
    WHERE status = 'ACTIVE' AND hidden = FALSE;
