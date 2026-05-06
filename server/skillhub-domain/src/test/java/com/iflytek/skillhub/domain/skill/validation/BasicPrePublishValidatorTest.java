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
    void shouldWarnOnObviousCredentialLeakWithHelpfulLocation() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Secret Skill
                version: 1.0.0
                ---
                token=sk-abcdefghijklmnopqrstuvwxyz123456
                """.getBytes(StandardCharsets.UTF_8),
                91,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Secret Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(error ->
                error.contains("SKILL.md")
                        && error.contains("line 5")
                        && error.contains("looks like a")));
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

    @Test
    void shouldIgnoreObviousPlaceholderSecrets() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Example Skill
                version: 1.0.0
                ---
                token=YOUR_TOKEN_HERE
                api_key=example-key-value
                """.getBytes(StandardCharsets.UTF_8),
                102,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
    }

    @Test
    void shouldIgnoreNonTextFiles() {
        PackageEntry binary = new PackageEntry(
                "image.png",
                new byte[]{0x00, 0x01, 0x02},
                3,
                "image/png"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(binary),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
    }

    @Test
    void shouldIgnorePlaceholderPatternWithOnlyX() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Example Skill
                version: 1.0.0
                ---
                token=xxxxxxxxxxxx
                """.getBytes(StandardCharsets.UTF_8),
                80,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
    }

    @Test
    void shouldIgnorePlaceholderPatternWithOnlyDash() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Example Skill
                version: 1.0.0
                ---
                token=------------
                """.getBytes(StandardCharsets.UTF_8),
                80,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
    }

    @Test
    void shouldWarnOnGitHubToken() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Example Skill
                version: 1.0.0
                ---
                token=ghp_abcdefghijklmnopqrstuvwxyz1234
                """.getBytes(StandardCharsets.UTF_8),
                90,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("GitHub token")));
    }

    @Test
    void shouldWarnOnCloudAccessKey() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Example Skill
                version: 1.0.0
                ---
                key=AKIAIOSFODNN7EXAMP01
                """.getBytes(StandardCharsets.UTF_8),
                80,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("cloud access key")));
    }

    @Test
    void shouldWarnOnSecretOrTokenPattern() {
        PackageEntry skillMd = new PackageEntry(
                "SKILL.md",
                """
                ---
                name: Example Skill
                version: 1.0.0
                ---
                api_key: AbCdEfGhIjKlMnOpQrStUvWxYz1234567890
                """.getBytes(StandardCharsets.UTF_8),
                90,
                "text/markdown"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(skillMd),
                new SkillMetadata("Example Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("secret or token")));
    }

    @Test
    void isPlaceholderValue_nullOrBlank_returnsFalse() throws Exception {
        java.lang.reflect.Method method = BasicPrePublishValidator.class.getDeclaredMethod("isPlaceholderValue", String.class);
        method.setAccessible(true);

        assertFalse((boolean) method.invoke(validator, (String) null));
        assertFalse((boolean) method.invoke(validator, ""));
        assertFalse((boolean) method.invoke(validator, "   "));
    }
}
