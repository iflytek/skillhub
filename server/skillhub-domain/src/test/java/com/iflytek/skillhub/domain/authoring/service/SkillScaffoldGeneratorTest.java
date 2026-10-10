package com.iflytek.skillhub.domain.authoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import org.junit.jupiter.api.Test;

class SkillScaffoldGeneratorTest {

    private final SkillScaffoldGenerator generator = new SkillScaffoldGenerator();
    private final SkillMetadataParser parser = new SkillMetadataParser();

    @Test
    void skillMdScaffoldHasValidFrontmatter() {
        String scaffold = generator.generateSkillMd("My Demo Skill", "It summarizes documents.");

        var metadata = parser.parse(scaffold);
        assertThat(metadata.name()).isEqualTo("my-demo-skill");
        assertThat(metadata.description()).isEqualTo("It summarizes documents.");
        assertThat(scaffold).contains("# my-demo-skill");
    }

    @Test
    void blankRequirementFallsBackToPlaceholder() {
        String scaffold = generator.generateSkillMd("demo", " ");

        var metadata = parser.parse(scaffold);
        assertThat(metadata.description()).isNotBlank();
        assertThat(scaffold).contains("Describe what this skill does");
    }

    @Test
    void validationYamlScaffoldShape() {
        String yaml = generator.generateValidationYaml();

        // the runtime layer's spec parser re-verifies parseability; here we pin
        // the scaffold's shape so the two cannot drift
        assertThat(yaml).contains("name: smoke");
        assertThat(yaml).contains("script: scripts/check.sh");
        assertThat(yaml).contains("type: script");
    }
}
