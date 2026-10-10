package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.user.UserAccount;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Authorization for metadata exposed by subscriptions and subscriber notifications. */
@Component
public class SubscriptionMetadataAccessPolicy {
    private final VisibilityChecker visibilityChecker;

    public SubscriptionMetadataAccessPolicy() {
        this(new VisibilityChecker());
    }

    @Autowired
    public SubscriptionMetadataAccessPolicy(VisibilityChecker visibilityChecker) {
        this.visibilityChecker = visibilityChecker;
    }

    public boolean canAccessCurrent(Skill skill, Namespace namespace, UserAccount account,
                                    Map<Long, NamespaceRole> namespaceRoles) {
        if (account == null || !account.isActive() || namespace == null) {
            return false;
        }
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED
                && !isOwnerOrManager(skill, account, namespaceRoles)) {
            return false;
        }
        return visibilityChecker.canAccess(skill, account.getId(), namespaceRoles);
    }

    public boolean canAccessYankedPublication(Skill skill, Namespace namespace, UserAccount account,
                                             Map<Long, NamespaceRole> namespaceRoles,
                                             boolean wasPublished) {
        return wasPublished && canAccess(skill, namespace, account, namespaceRoles, true);
    }

    private boolean canAccess(Skill skill, Namespace namespace, UserAccount account,
                              Map<Long, NamespaceRole> namespaceRoles, boolean yankedPublication) {
        if (account == null || !account.isActive() || namespace == null) {
            return false;
        }
        Map<Long, NamespaceRole> roles = namespaceRoles == null ? Map.of() : namespaceRoles;
        NamespaceRole role = roles.get(skill.getNamespaceId());
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED && role == null) {
            return false;
        }
        boolean owner = skill.getOwnerId().equals(account.getId());
        boolean manager = role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
        if (skill.isHidden()) {
            return owner || manager;
        }
        if (!yankedPublication && skill.getLatestVersionId() == null) {
            return owner;
        }
        return switch (skill.getVisibility()) {
            case PUBLIC -> true;
            case NAMESPACE_ONLY -> role != null;
            case PRIVATE -> owner || manager;
        };
    }

    private boolean isOwnerOrManager(Skill skill, UserAccount account,
                                     Map<Long, NamespaceRole> namespaceRoles) {
        if (skill.getOwnerId().equals(account.getId())) {
            return true;
        }
        NamespaceRole role = namespaceRoles == null ? null : namespaceRoles.get(skill.getNamespaceId());
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }
}
