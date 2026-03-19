package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;

import java.util.Map;

/**
 * Evaluates whether a caller may read a skill based on publication state, visibility, ownership,
 * and namespace roles.
 */
public class VisibilityChecker {

    public boolean canAccess(Skill skill, String currentUserId, Map<Long, NamespaceRole> userNamespaceRoles) {
        if (skill.isHidden()) {
            return isOwner(skill, currentUserId) || isAdminOrAbove(userNamespaceRoles.get(skill.getNamespaceId()));
        }
        if (skill.getLatestVersionId() == null) {
            return isOwner(skill, currentUserId);
        }
        return switch (skill.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> userNamespaceRoles.containsKey(skill.getNamespaceId());
            case PRIVATE -> isOwner(skill, currentUserId) || isAdminOrAbove(userNamespaceRoles.get(skill.getNamespaceId()));
        };
    }

    private boolean isOwner(Skill skill, String currentUserId) {
        return currentUserId != null && skill.getOwnerId().equals(currentUserId);
    }

    private boolean isAdminOrAbove(NamespaceRole role) {
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }
}
