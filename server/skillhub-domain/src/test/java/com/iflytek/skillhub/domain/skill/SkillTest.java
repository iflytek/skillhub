package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SkillTest {

    @Test
    void constructorInitializesFields() {
        Skill skill = new Skill(1L, "my-skill", "user-1", SkillVisibility.PUBLIC);

        assertThat(skill.getNamespaceId()).isEqualTo(1L);
        assertThat(skill.getSlug()).isEqualTo("my-skill");
        assertThat(skill.getOwnerId()).isEqualTo("user-1");
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(skill.getStatus()).isEqualTo(SkillStatus.ACTIVE);
    }

    @Test
    void prePersistSetsTimestamps() {
        Skill skill = new Skill(1L, "skill", "user-1", SkillVisibility.PRIVATE);

        skill.onCreate();

        assertThat(skill.getCreatedAt()).isNotNull();
        assertThat(skill.getUpdatedAt()).isNotNull();
        assertThat(skill.getCreatedAt()).isEqualTo(skill.getUpdatedAt());
    }

    @Test
    void preUpdateSetsUpdatedAt() {
        Skill skill = new Skill(1L, "skill", "user-1", SkillVisibility.PRIVATE);
        skill.onCreate();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        skill.onUpdate();

        assertThat(skill.getUpdatedAt()).isAfterOrEqualTo(skill.getCreatedAt());
    }

    @Test
    void gettersAndSettersWork() {
        Skill skill = new Skill(1L, "skill", "user-1", SkillVisibility.PUBLIC);
        skill.onCreate();

        assertThat(skill.getId()).isNull();
        assertThat(skill.getNamespaceId()).isEqualTo(1L);
        assertThat(skill.getSlug()).isEqualTo("skill");
        assertThat(skill.getOwnerId()).isEqualTo("user-1");
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.PUBLIC);
        assertThat(skill.getStatus()).isEqualTo(SkillStatus.ACTIVE);
        assertThat(skill.getCreatedAt()).isNotNull();
        assertThat(skill.getUpdatedAt()).isNotNull();

        assertThat(skill.getCreatedBy()).isNull();
        skill.setCreatedBy("admin");
        assertThat(skill.getCreatedBy()).isEqualTo("admin");

        assertThat(skill.getDisplayName()).isNull();
        skill.setDisplayName("Display Name");
        assertThat(skill.getDisplayName()).isEqualTo("Display Name");

        assertThat(skill.getSummary()).isNull();
        skill.setSummary("Summary text");
        assertThat(skill.getSummary()).isEqualTo("Summary text");

        assertThat(skill.getSourceSkillId()).isNull();
        skill.setSourceSkillId(99L);
        assertThat(skill.getSourceSkillId()).isEqualTo(99L);

        assertThat(skill.getLatestVersionId()).isNull();
        skill.setLatestVersionId(10L);
        assertThat(skill.getLatestVersionId()).isEqualTo(10L);

        skill.setStatus(SkillStatus.ARCHIVED);
        assertThat(skill.getStatus()).isEqualTo(SkillStatus.ARCHIVED);

        skill.setVisibility(SkillVisibility.NAMESPACE_ONLY);
        assertThat(skill.getVisibility()).isEqualTo(SkillVisibility.NAMESPACE_ONLY);

        skill.setUpdatedBy(" updater ");
        assertThat(skill.getUpdatedBy()).isEqualTo(" updater ");

        assertThat(skill.getDownloadCount()).isEqualTo(0L);
        assertThat(skill.isHidden()).isFalse();
        assertThat(skill.getHiddenAt()).isNull();
        assertThat(skill.getHiddenBy()).isNull();
        assertThat(skill.getStarCount()).isEqualTo(0);
        assertThat(skill.getRatingAvg()).isEqualTo(BigDecimal.ZERO);
        assertThat(skill.getRatingCount()).isEqualTo(0);
    }

    @Test
    void hiddenSettersWork() {
        Skill skill = new Skill(1L, "skill", "user-1", SkillVisibility.PUBLIC);

        Instant now = Instant.now();
        skill.setHidden(true);
        skill.setHiddenAt(now);
        skill.setHiddenBy("admin");

        assertThat(skill.isHidden()).isTrue();
        assertThat(skill.getHiddenAt()).isEqualTo(now);
        assertThat(skill.getHiddenBy()).isEqualTo("admin");
    }

    @Test
    void protectedConstructorExistsForJpa() {
        Skill skill = new Skill();
        assertThat(skill).isNotNull();
    }
}
