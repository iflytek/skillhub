package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSuiteMemberEligibilityPolicyTest {

    private final SkillSuiteMemberEligibilityPolicy policy = new SkillSuiteMemberEligibilityPolicy();

    @Test
    void publicSuiteRejectsNonPublicMember() {
        SkillSuiteMemberState member = member(2L, SkillVisibility.NAMESPACE_ONLY);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.VISIBILITY_INCOMPATIBLE);
    }

    @Test
    void privateSuiteAcceptsPrivateMemberFromSameNamespace() {
        SkillSuiteMemberState member = member(1L, SkillVisibility.PRIVATE);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PRIVATE, member);

        assertThat(result.available()).isTrue();
    }

    @Test
    void suiteAcceptsPublicMemberFromAnotherNamespace() {
        SkillSuiteMemberState member = member(2L, SkillVisibility.PUBLIC);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isTrue();
    }

    @Test
    void deletedReferenceProducesTombstoneInsteadOfRelinkingByCoordinate() {
        SkillSuiteMemberState member = SkillSuiteMemberState.deleted(10L, 20L);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PRIVATE, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.DELETED);
    }

    @Test
    void unpublishedOrNotDownloadReadyVersionIsUnavailable() {
        SkillSuiteMemberState member = new SkillSuiteMemberState(
                10L, 20L, 1L, null, null,
                NamespaceStatus.ACTIVE, SkillVisibility.PUBLIC, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, false, false, false);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.VERSION_UNAVAILABLE);
    }

    @Test
    void hiddenMemberSkillIsUnavailable() {
        SkillSuiteMemberState member = member(
                NamespaceStatus.ACTIVE, SkillVisibility.PUBLIC, SkillStatus.ACTIVE, true,
                SkillVersionStatus.PUBLISHED, true, false);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.SKILL_HIDDEN);
    }

    @Test
    void archivedMemberSkillIsUnavailable() {
        SkillSuiteMemberState member = member(
                NamespaceStatus.ACTIVE, SkillVisibility.PUBLIC, SkillStatus.ARCHIVED, false,
                SkillVersionStatus.PUBLISHED, true, false);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.SKILL_ARCHIVED);
    }

    @Test
    void yankedMemberVersionIsUnavailable() {
        SkillSuiteMemberState member = member(
                NamespaceStatus.ACTIVE, SkillVisibility.PUBLIC, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, true, true);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.VERSION_UNAVAILABLE);
    }

    @Test
    void reversibleMemberRestrictionRecoveryMakesMemberAvailableAgain() {
        SkillSuiteMemberState member = member(
                NamespaceStatus.ACTIVE, SkillVisibility.PUBLIC, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, true, false);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void archivedMemberNamespaceMakesExactMemberUnavailable() {
        SkillSuiteMemberState member = new SkillSuiteMemberState(
                10L, 20L, 1L, null, null, NamespaceStatus.ARCHIVED, SkillVisibility.PUBLIC,
                SkillStatus.ACTIVE, false, SkillVersionStatus.PUBLISHED, true, false, false);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.NAMESPACE_ARCHIVED);
    }

    @Test
    void frozenMemberNamespaceHasItsOwnBlockingReason() {
        SkillSuiteMemberState member = new SkillSuiteMemberState(
                10L, 20L, 1L, null, null, NamespaceStatus.FROZEN, SkillVisibility.PUBLIC,
                SkillStatus.ACTIVE, false, SkillVersionStatus.PUBLISHED, true, false, false);

        SkillSuiteMemberAvailability result = policy.evaluate(1L, SkillVisibility.PUBLIC, member);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(SkillSuiteMemberBlockingReason.NAMESPACE_FROZEN);
    }

    private SkillSuiteMemberState member(Long namespaceId, SkillVisibility visibility) {
        return new SkillSuiteMemberState(
                10L, 20L, namespaceId, null, null,
                NamespaceStatus.ACTIVE, visibility, SkillStatus.ACTIVE, false,
                SkillVersionStatus.PUBLISHED, true, false, false);
    }

    private SkillSuiteMemberState member(
            NamespaceStatus namespaceStatus,
            SkillVisibility visibility,
            SkillStatus skillStatus,
            boolean hidden,
            SkillVersionStatus versionStatus,
            boolean downloadReady,
            boolean yanked
    ) {
        return new SkillSuiteMemberState(
                10L, 20L, 1L, null, null, namespaceStatus, visibility,
                skillStatus, hidden, versionStatus, downloadReady, yanked, false);
    }
}
