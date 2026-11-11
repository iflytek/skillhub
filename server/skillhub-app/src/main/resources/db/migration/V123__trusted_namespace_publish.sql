-- Default-off, per-TEAM and per-member grant; pending tasks retain their own decision marker.
ALTER TABLE namespace ADD COLUMN trusted_publish_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE namespace_member ADD COLUMN review_exempt BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE review_task ADD COLUMN auto_approve_on_safe_scan BOOLEAN NOT NULL DEFAULT FALSE;
