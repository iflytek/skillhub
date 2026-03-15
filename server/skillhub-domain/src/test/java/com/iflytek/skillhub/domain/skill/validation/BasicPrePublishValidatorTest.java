package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicPrePublishValidatorTest {

    private final BasicPrePublishValidator validator = new BasicPrePublishValidator();

    @Test
    void shouldRejectObviousCredentialLeak() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Secret Skill
                version: 1.0.0
                ---
                """.getBytes(StandardCharsets.UTF_8),
                47,
                "text/markdown"
        );
        PackageEntry config = new PackageEntry(
                "config.txt",
                "OPENAI_API_KEY=sk-abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8),
                50,
                "text/plain"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd, config),
                new SkillMetadata("Secret Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertFalse(result.passed());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("Potential secret detected")));
    }

    @Test
    void shouldAllowOrdinaryTextFiles() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Safe Skill
                version: 1.0.0
                ---
                """.getBytes(StandardCharsets.UTF_8),
                45,
                "text/markdown"
        );
        PackageEntry readme = new PackageEntry(
                "README.md",
                "This skill documents safe usage.".getBytes(StandardCharsets.UTF_8),
                31,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd, readme),
                new SkillMetadata("Safe Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
    }
}
