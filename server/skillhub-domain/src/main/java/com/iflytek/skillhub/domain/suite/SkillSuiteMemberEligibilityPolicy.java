package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;

/** Evaluates the current installability of an exact member against the Suite's approved audience. */
public final class SkillSuiteMemberEligibilityPolicy {

    public SkillSuiteMemberAvailability evaluate(
            Long suiteNamespaceId,
            SkillVisibility suiteVisibility,
            SkillSuiteMemberState member
    ) {
        if (member == null || member.tombstoned()) {
            return SkillSuiteMemberAvailability.blocked(SkillSuiteMemberBlockingReason.DELETED);
        }
        if (member.hidden()) {
            return SkillSuiteMemberAvailability.blocked(SkillSuiteMemberBlockingReason.SKILL_HIDDEN);
        }
        if (member.namespaceStatus() == NamespaceStatus.ARCHIVED) {
            return SkillSuiteMemberAvailability.blocked(SkillSuiteMemberBlockingReason.NAMESPACE_ARCHIVED);
        }
        if (member.namespaceStatus() == NamespaceStatus.FROZEN) {
            return SkillSuiteMemberAvailability.blocked(SkillSuiteMemberBlockingReason.NAMESPACE_FROZEN);
        }
        if (member.skillStatus() != SkillStatus.ACTIVE) {
            return SkillSuiteMemberAvailability.blocked(SkillSuiteMemberBlockingReason.SKILL_ARCHIVED);
        }
        if (member.versionStatus() != SkillVersionStatus.PUBLISHED || !member.downloadReady() || member.yanked()) {
            return SkillSuiteMemberAvailability.blocked(SkillSuiteMemberBlockingReason.VERSION_UNAVAILABLE);
        }
        if (!audienceCanReadMember(suiteNamespaceId, suiteVisibility, member.namespaceId(), member.visibility())) {
            return SkillSuiteMemberAvailability.blocked(SkillSuiteMemberBlockingReason.VISIBILITY_INCOMPATIBLE);
        }
        return SkillSuiteMemberAvailability.availableMember();
    }

    private boolean audienceCanReadMember(
            Long suiteNamespaceId,
            SkillVisibility suiteVisibility,
            Long memberNamespaceId,
            SkillVisibility memberVisibility
    ) {
        if (memberVisibility == SkillVisibility.PUBLIC) {
            return true;
        }
        if (suiteVisibility == SkillVisibility.PUBLIC || !suiteNamespaceId.equals(memberNamespaceId)) {
            return false;
        }
        if (suiteVisibility == SkillVisibility.NAMESPACE_ONLY) {
            return memberVisibility == SkillVisibility.NAMESPACE_ONLY;
        }
        return suiteVisibility == SkillVisibility.PRIVATE;
    }
}
