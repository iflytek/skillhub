package com.iflytek.skillhub.domain.label;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillLabelTest {

    @Test
    void constructorInitializesFields() {
        SkillLabel skillLabel = new SkillLabel(1L, 2L, "user-1");

        assertThat(skillLabel.getSkillId()).isEqualTo(1L);
        assertThat(skillLabel.getLabelId()).isEqualTo(2L);
        assertThat(skillLabel.getCreatedBy()).isEqualTo("user-1");
    }

    @Test
    void prePersistSetsCreatedAt() {
        SkillLabel skillLabel = new SkillLabel(1L, 2L, "user-1");

        skillLabel.onCreate();

        assertThat(skillLabel.getCreatedAt()).isNotNull();
    }

    @Test
    void gettersWork() {
        SkillLabel skillLabel = new SkillLabel(1L, 2L, "user-1");
        skillLabel.onCreate();

        assertThat(skillLabel.getId()).isNull();
        assertThat(skillLabel.getSkillId()).isEqualTo(1L);
        assertThat(skillLabel.getLabelId()).isEqualTo(2L);
        assertThat(skillLabel.getCreatedBy()).isEqualTo("user-1");
        assertThat(skillLabel.getCreatedAt()).isNotNull();
    }

    @Test
    void protectedConstructorExistsForJpa() {
        SkillLabel skillLabel = new SkillLabel();
        assertThat(skillLabel).isNotNull();
    }
}
