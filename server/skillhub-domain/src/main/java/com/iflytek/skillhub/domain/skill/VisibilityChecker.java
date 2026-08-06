package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;

import java.util.Map;
import java.util.Set;

/**
 * Evaluates whether a caller may read a skill based on publication state, visibility, ownership,
 * and namespace roles.
 */
public class VisibilityChecker {

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNamespaceRoles) {
        return canAccess(skill, currentUserId, userNamespaceRoles, Set.of());
    }

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNamespaceRoles, Set<String> platformRoles) {
        Map<Long, NamespaceRole> roles = userNamespaceRoles != null ? userNamespaceRoles : Map.of();
        if (isSuperAdmin(platformRoles)) {
            return true;
        }
        if (skill.isHidden()) {
            return isOwner(skill, currentUserId) || isAdminOrAbove(roles.get(skill.getNamespaceId()));
        }
        if (skill.getLatestVersionId() == null) {
            return isOwner(skill, currentUserId);
        }
        return switch (skill.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> roles.containsKey(skill.getNamespaceId());
            case PRIVATE -> isOwner(skill, currentUserId) || isAdminOrAbove(roles.get(skill.getNamespaceId()));
        };
    }

    /**
     * Applies portal namespace-read semantics without turning the caller into a namespace member.
     * The override exposes published namespace-visible skills, but never private, hidden, or
     * unpublished skills.
     */
    public boolean canAccessForNamespaceRead(
            Skill skill,
            String currentUserId,
            Map<Long, NamespaceRole> userNamespaceRoles,
            boolean namespaceReadAllowed) {
        if (canAccess(skill, currentUserId, userNamespaceRoles)) {
            return true;
        }
        return namespaceReadAllowed
                && !skill.isHidden()
                && skill.getStatus() == SkillStatus.ACTIVE
                && skill.getLatestVersionId() != null
                && skill.getVisibility() == SkillVisibility.NAMESPACE_ONLY;
    }

    private boolean isOwner(Skill skill, String currentUserId) {
        return currentUserId != null && skill.getOwnerId().equals(currentUserId);
    }

    private boolean isAdminOrAbove(NamespaceRole role) {
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains("SUPER_ADMIN");
    }
}
