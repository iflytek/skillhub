-- Allow soft-deleted security audit history to survive skill version deletion.
-- The application stores skill_version_id as a plain identifier and intentionally
-- soft deletes security_audit rows before removing draft/rejected versions.

ALTER TABLE security_audit
    DROP CONSTRAINT IF EXISTS security_audit_skill_version_id_fkey;
