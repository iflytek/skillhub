-- Reverse Skill-to-Suite discovery starts from any current snapshot member, not only the entry.
CREATE INDEX idx_skill_suite_member_skill
    ON skill_suite_version_member(skill_id)
    WHERE skill_id IS NOT NULL;
