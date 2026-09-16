package com.iflytek.skillhub.domain.suite;

/** Computed availability; it is deliberately not persisted as a Suite lifecycle state. */
public record SkillSuiteMemberAvailability(boolean available, SkillSuiteMemberBlockingReason reason) {

    public static SkillSuiteMemberAvailability availableMember() {
        return new SkillSuiteMemberAvailability(true, null);
    }

    public static SkillSuiteMemberAvailability blocked(SkillSuiteMemberBlockingReason reason) {
        return new SkillSuiteMemberAvailability(false, reason);
    }
}
