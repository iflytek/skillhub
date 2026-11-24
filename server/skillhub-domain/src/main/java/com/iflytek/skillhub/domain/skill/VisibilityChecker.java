package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;

import java.util.Map;
import java.util.Set;

/**
 * Evaluates whether a caller may read a skill based on publication state, visibility, ownership,
 * and namespace roles. Ownership counts only while {@link SkillOwnerAccess} allows it.
 */
public class VisibilityChecker {

    private final SkillOwnerAccess ownerAccess;

    public VisibilityChecker(SkillOwnerAccess ownerAccess) {
        this.ownerAccess = ownerAccess;
    }

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNamespaceRoles) {
        return canAccess(skill, currentUserId, userNamespaceRoles, Set.of());
    }

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNamespaceRoles, Set<String> platformRoles) {
        Map<Long, NamespaceRole> roles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        if (isSuperAdmin(platformRoles)) {
            return true;
        }
        if (skill.isHidden()) {
            return isAdminOrAbove(roles.get(skill.getNamespaceId())) || canReadAsOwner(skill, currentUserId);
        }
        if (skill.getLatestVersionId() == null) {
            return canReadAsOwner(skill, currentUserId);
        }
        return switch (skill.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> roles.containsKey(skill.getNamespaceId()) || canReadAsOwner(skill, currentUserId);
            case PRIVATE -> isAdminOrAbove(roles.get(skill.getNamespaceId())) || canReadAsOwner(skill, currentUserId);
        };
    }

    /** True when the user owns the skill and may still read its internal content as the owner. */
    public boolean canReadAsOwner(Skill skill, String currentUserId) {
        return isOwner(skill, currentUserId) && ownerAccess.ownerRetainsAccess(skill);
    }

    /** True when the user owns the skill but ownership no longer grants reads, because they left its TEAM namespace. */
    public boolean isDepartedOwner(Skill skill, String currentUserId) {
        return isOwner(skill, currentUserId) && !ownerAccess.ownerRetainsAccess(skill);
    }

    /** The denial for a read: an owner who has left the TEAM namespace is told that their access ended. */
    public static DomainForbiddenException readDenied(Skill skill, boolean departedOwner) {
        return new DomainForbiddenException(
                departedOwner ? "error.skill.owner.membershipEnded" : "error.skill.access.denied", skill.getSlug());
    }

    private boolean isOwner(Skill skill, String currentUserId) {
        return currentUserId != null && currentUserId.equals(skill.getOwnerId());
    }

    private boolean isAdminOrAbove(NamespaceRole role) {
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains("SUPER_ADMIN");
    }
}
