package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Ties what a TEAM skill's owner may do to current namespace membership. An owner who has left the namespace
 * can neither read the skill's internal content nor maintain it; public content stays readable as for anyone.
 */
@Component
public class SkillOwnerAccessPolicy implements SkillOwnerAccess {
    private final NamespaceRepository namespaces;
    private final NamespaceMemberRepository members;

    public SkillOwnerAccessPolicy(NamespaceRepository namespaces, NamespaceMemberRepository members) {
        this.namespaces = namespaces;
        this.members = members;
    }

    @Override
    public boolean ownerRetainsAccess(Skill skill) {
        return !ownerLeftTeamNamespace(skill);
    }

    /** True when the actor owns the skill but has left its TEAM namespace, unless SUPER_ADMIN acts. */
    public boolean isReadOnly(Skill skill, String actor, Set<String> platformRoles) {
        if (actor == null || !actor.equals(skill.getOwnerId())
                || platformRoles != null && platformRoles.contains("SUPER_ADMIN")) {
            return false;
        }
        return ownerLeftTeamNamespace(skill);
    }

    public void assertCanWrite(Skill skill, String actor, Set<String> platformRoles) {
        if (isReadOnly(skill, actor, platformRoles)) {
            throw new DomainForbiddenException("error.skill.owner.readOnly");
        }
    }

    private boolean ownerLeftTeamNamespace(Skill skill) {
        var namespace = namespaces.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", skill.getNamespaceId()));
        return namespace.getType() == NamespaceType.TEAM
                && members.findByNamespaceIdAndUserId(skill.getNamespaceId(), skill.getOwnerId()).isEmpty();
    }
}
