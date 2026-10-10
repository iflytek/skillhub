package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.namespace.SlugValidator;
import org.springframework.stereotype.Service;

/**
 * Generates the initial SKILL.md scaffold for a new draft from the author's name and
 * requirement description. Template-based by design: scaffolds are a starting point
 * the author edits and validates; model-assisted generation remains an optional
 * extension layered on top of this deterministic baseline.
 */
@Service
public class SkillScaffoldGenerator {

    /**
     * Builds the scaffold SKILL.md content.
     *
     * @param draftName   the draft name chosen by the author
     * @param requirement the natural-language requirement description
     */
    public String generateSkillMd(String draftName, String requirement) {
        String skillName = safeName(draftName);
        String overview = requirement == null || requirement.isBlank()
                ? "Describe what this skill does and when an agent should use it."
                : requirement.strip();
        return """
                ---
                name: %s
                description: %s
                ---

                # %s

                ## Overview

                %s

                ## Usage

                Describe step by step how an agent should apply this skill.

                ## Resources

                - `references/` — background material the skill can cite
                - `scripts/` — executable helpers invoked during task execution
                """.formatted(skillName, singleLine(overview), skillName, overview);
    }

    /** Builds a starter validation.yaml with one script task template. */
    public String generateValidationYaml() {
        return """
                version: 1
                tasks:
                  - name: smoke
                    description: Replace with a real check for this skill
                    type: script
                    script: scripts/check.sh
                    args: []
                    timeoutMs: 30000
                    assertions:
                      - type: exit_code
                        equals: 0
                """;
    }

    private String safeName(String draftName) {
        try {
            String slug = SlugValidator.slugify(draftName);
            return slug == null || slug.isBlank() ? "my-skill" : slug;
        } catch (Exception exception) {
            return "my-skill";
        }
    }

    private String singleLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
