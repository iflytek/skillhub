package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.skill.SkillVisibility;

import java.util.ArrayList;
import java.util.List;

/** Live aggregate availability derived from exact member state; never stored as lifecycle state. */
public record SkillSuiteAvailability(boolean available, List<SkillSuiteBlockedMember> blockedMembers) {

    public SkillSuiteAvailability {
        blockedMembers = List.copyOf(blockedMembers);
    }

    public static SkillSuiteAvailability evaluate(
            Long suiteNamespaceId,
            SkillVisibility suiteVisibility,
            List<SkillSuiteMemberState> members
    ) {
        SkillSuiteMemberEligibilityPolicy policy = new SkillSuiteMemberEligibilityPolicy();
        List<SkillSuiteBlockedMember> blocked = new ArrayList<>();
        for (SkillSuiteMemberState member : members) {
            SkillSuiteMemberAvailability result = policy.evaluate(suiteNamespaceId, suiteVisibility, member);
            if (!result.available()) {
                blocked.add(new SkillSuiteBlockedMember(member.skillVersionId(), result.reason()));
            }
        }
        return new SkillSuiteAvailability(blocked.isEmpty(), blocked);
    }
}
