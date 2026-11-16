package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Single owner of the team-only automatic publication scope rules.
 *
 * <p>The permission covers only ordinary submissions that keep a Skill team-only: the first
 * publication of a new Skill directly as {@code NAMESPACE_ONLY}, or a new version of a Skill that is
 * already {@code NAMESPACE_ONLY}. Any visibility change, including an upload whose target differs
 * from the Skill's current visibility, is out of scope. Specialized workflows never call this policy.
 */
@Component
public class TeamAutoPublicationPolicy {

    private final NamespaceMemberRepository namespaceMemberRepository;

    public TeamAutoPublicationPolicy(NamespaceMemberRepository namespaceMemberRepository) {
        this.namespaceMemberRepository = namespaceMemberRepository;
    }

    /**
     * Returns the active grant identity to capture for an accepted ordinary submission.
     *
     * @param maySubmit whether the accepted request carried submit capability; the route policy
     *                  decides this before the domain is reached
     */
    public Optional<UUID> grantForSubmission(Namespace namespace,
                                             Skill skill,
                                             boolean newSkill,
                                             SkillVisibility targetVisibility,
                                             String submitterId,
                                             boolean maySubmit) {
        if (!maySubmit || targetVisibility != SkillVisibility.NAMESPACE_ONLY || !isActiveTeam(namespace)) {
            return Optional.empty();
        }
        if (!newSkill && skill.getVisibility() != SkillVisibility.NAMESPACE_ONLY) {
            return Optional.empty();
        }
        if (skill.isGithubSyncManaged() || !submitterId.equals(skill.getOwnerId())) {
            return Optional.empty();
        }
        return activeGrant(namespace.getId(), submitterId);
    }

    /** Current grant identity of an ordinary member, or empty when the member is not eligible. */
    public Optional<UUID> activeGrant(Long namespaceId, String userId) {
        return namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, userId)
                .filter(member -> member.getRole() == NamespaceRole.MEMBER)
                .map(NamespaceMember::getTeamAutoPublicationGrantId);
    }

    public boolean isActiveTeam(Namespace namespace) {
        return namespace != null
                && namespace.getType() == NamespaceType.TEAM
                && namespace.getStatus() == NamespaceStatus.ACTIVE;
    }
}
