package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillSuiteVersionTest {

    @Test
    void draftIsEditableButPublishedVersionIsImmutable() {
        SkillSuiteVersion version = new SkillSuiteVersion(1L, "1.0.0", SkillVisibility.PUBLIC, "user-1");

        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.DRAFT);
        assertThatCode(version::assertEditable).doesNotThrowAnyException();

        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);

        assertThatThrownBy(version::assertEditable)
                .isInstanceOf(DomainBadRequestException.class)
                .extracting("messageCode")
                .isEqualTo("error.suite.version.immutable");
    }
}
