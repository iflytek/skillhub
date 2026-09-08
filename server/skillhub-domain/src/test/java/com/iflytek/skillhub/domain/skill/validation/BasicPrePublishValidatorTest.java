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
    void shouldAllowRuntimeExpressionsAssignedToSensitiveVariables() {
        PackageEntry script = new PackageEntry(
                "scripts/oauth.py",
                """
                refresh_token = token_response.get("refresh_token")
                client_secret = client_secret
                self._client_secret = client_secret
                access_token = ensure_valid_access_token(config)
                headers = build_headers(access_token=access_token)
                token = response.data["token"]
                """.getBytes(StandardCharsets.UTF_8),
                256,
                "text/x-python"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(script),
                new SkillMetadata("Safe Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void shouldWarnOnQuotedAndBareCredentialLiterals() {
        PackageEntry script = new PackageEntry(
                "scripts/config.py",
                """
                client_secret = "abcdefghijklmnop"  // hardcoded
                refresh_token=zyxwvuts-12345678 # hardcoded
                """.getBytes(StandardCharsets.UTF_8),
                96,
                "text/x-python"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(script),
                new SkillMetadata("Unsafe Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertTrue(result.warnings().stream().anyMatch(warning ->
                warning.contains("scripts/config.py line 1")));
        assertTrue(result.warnings().stream().anyMatch(warning ->
                warning.contains("scripts/config.py line 2")));
    }

    @Test
    void shouldStillWarnOnProviderSpecificTokensInsideExpressions() {
        PackageEntry script = new PackageEntry(
                "scripts/client.py",
                """
                headers = build_headers("ghp_abcdefghijklmnopqrstuvwxyz1234")
                """.getBytes(StandardCharsets.UTF_8),
                68,
                "text/x-python"
        );

        ValidationResult result = validator.validate(new PrePublishValidator.SkillPackageContext(
                List.of(script),
                new SkillMetadata("Unsafe Skill", "desc", "1.0.0", "body", Map.of()),
                "user-1",
                1L
        ));

        assertTrue(result.passed());
        assertFalse(result.warnings().isEmpty());
        assertTrue(result.warnings().getFirst().contains("GitHub token"));
    }
}
