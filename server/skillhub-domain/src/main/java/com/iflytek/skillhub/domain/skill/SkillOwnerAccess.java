package com.iflytek.skillhub.domain.skill;

/** Decides whether a skill's owner may still rely on ownership to read the skill's internal content. */
@FunctionalInterface
public interface SkillOwnerAccess {

    /**
     * Owners keep this access outside TEAM namespaces. In a TEAM namespace they keep it only while they are
     * current members: neither current nor past ownership outlasts membership.
     */
    boolean ownerRetainsAccess(Skill skill);
}
