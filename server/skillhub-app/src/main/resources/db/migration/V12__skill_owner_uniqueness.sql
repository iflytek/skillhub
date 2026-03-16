-- V12__skill_owner_uniqueness.sql
-- Change skill uniqueness from (namespace_id, slug) to (namespace_id, slug, owner_id)
-- to support owner-isolated skill records with the same name

ALTER TABLE skill DROP CONSTRAINT skill_namespace_id_slug_key;
ALTER TABLE skill ADD CONSTRAINT skill_namespace_id_slug_owner_id_key UNIQUE(namespace_id, slug, owner_id);
