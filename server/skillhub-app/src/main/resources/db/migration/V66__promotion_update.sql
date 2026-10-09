ALTER TABLE promotion_request
    ADD COLUMN request_kind VARCHAR(16) NOT NULL DEFAULT 'INITIAL',
    ADD COLUMN target_version_id BIGINT;

UPDATE promotion_request p
SET target_version_id = target_version.id
FROM skill_version source_version, skill_version target_version
WHERE p.status = 'APPROVED'
  AND p.source_version_id = source_version.id
  AND p.target_skill_id = target_version.skill_id
  AND source_version.version = target_version.version
  AND p.request_kind = 'INITIAL';

CREATE UNIQUE INDEX uq_promotion_source_pending
    ON promotion_request(source_skill_id) WHERE status = 'PENDING';

CREATE UNIQUE INDEX uq_promotion_active_initial
    ON promotion_request(source_skill_id)
    WHERE request_kind = 'INITIAL' AND status = 'APPROVED' AND target_skill_id IS NOT NULL;
