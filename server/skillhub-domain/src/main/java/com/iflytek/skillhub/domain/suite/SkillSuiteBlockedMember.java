package com.iflytek.skillhub.domain.suite;

/** Non-sensitive identity and reason for one blocked exact member. */
public record SkillSuiteBlockedMember(Long skillVersionId, SkillSuiteMemberBlockingReason reason) {
}
