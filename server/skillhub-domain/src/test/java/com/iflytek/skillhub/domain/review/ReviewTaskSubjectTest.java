package com.iflytek.skillhub.domain.review;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskSubjectTest {

    @Test
    void existingSkillConstructorPopulatesTypedSubject() {
        ReviewTask task = new ReviewTask(20L, 10L, 1L, "1.0.0", "author");

        assertThat(task.getSubjectType()).isEqualTo(ReviewSubjectType.SKILL_VERSION);
        assertThat(task.getSubjectId()).isEqualTo(10L);
        assertThat(task.getSubjectVersionId()).isEqualTo(20L);
        assertThat(task.getSubjectVersion()).isEqualTo("1.0.0");
    }

    @Test
    void suiteFactoryDoesNotPretendSuiteIsASkill() {
        ReviewTask task = ReviewTask.forSuiteVersion(40L, 30L, 1L, "2.0.0", "author");

        assertThat(task.getSubjectType()).isEqualTo(ReviewSubjectType.SUITE_VERSION);
        assertThat(task.getSubjectId()).isEqualTo(30L);
        assertThat(task.getSubjectVersionId()).isEqualTo(40L);
        assertThat(task.getSkillId()).isNull();
        assertThat(task.getSkillVersionId()).isNull();
    }
}
