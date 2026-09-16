package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSuiteAvailabilityTest {

    @Test
    void suiteIsDegradedWhenAnyExactMemberIsBlocked() {
        SkillSuiteMemberState available = member(10L, 100L, true);
        SkillSuiteMemberState unavailable = member(20L, 200L, false);

        SkillSuiteAvailability result = SkillSuiteAvailability.evaluate(
                1L, SkillVisibility.PUBLIC, List.of(available, unavailable));

        assertThat(result.available()).isFalse();
        assertThat(result.blockedMembers()).containsExactly(
                new SkillSuiteBlockedMember(200L, SkillSuiteMemberBlockingReason.VERSION_UNAVAILABLE));
    }

    private SkillSuiteMemberState member(Long skillId, Long versionId, boolean downloadReady) {
        return new SkillSuiteMemberState(
                skillId, versionId, 1L, null, null,
                NamespaceStatus.ACTIVE, SkillVisibility.PUBLIC, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, downloadReady, false, false);
    }
}
