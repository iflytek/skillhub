package com.iflytek.skillhub.domain.skill.service;

import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Service for submitting skill versions for review and confirming private publishes.
 *
 * <p>This service handles two key workflows for UPLOADED skill versions:
 * <ul>
 *   <li><b>submitForReview</b>: Transitions an UPLOADED version to PENDING_REVIEW status,
 *       creating a review task for PUBLIC/NAMESPACE_ONLY visibility changes.</li>
 *   <li><b>confirmPublish</b>: Transitions an UPLOADED version directly to PUBLISHED status
 *       for PRIVATE skills without requiring review.</li>
 * </ul>
 *
 * @see SkillVersionStatus#UPLOADED
 * @see SkillVisibility#PRIVATE
 */
@Service
public class SkillReviewSubmitService {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SkillReviewSubmitService(
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            ReviewTaskRepository reviewTaskRepository,
            NamespaceMemberRepository namespaceMemberRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Submit an UPLOADED or DRAFT version for review.
     * Transitions version status from UPLOADED/DRAFT to PENDING_REVIEW.
     *
     * <p>Supports both UPLOADED (new flow) and DRAFT (legacy compatibility) status.
     *
     * @param skillId          the skill ID
     * @param versionId        the version ID
     * @param targetVisibility the target visibility after approval
     * @param actorUserId      the user performing the action
     * @param userNamespaceRoles user's namespace roles
     */
    @Transactional
    public void submitForReview(Long skillId, Long versionId, SkillVisibility targetVisibility,
                                String actorUserId, Map<Long, NamespaceRole> userNamespaceRoles) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionId));

        // Validate ownership
        assertCanManageLifecycle(skill, actorUserId, userNamespaceRoles);

        // Validate version status - support both UPLOADED (new) and DRAFT (legacy)
        if (version.getStatus() != SkillVersionStatus.UPLOADED
                && version.getStatus() != SkillVersionStatus.DRAFT) {
            throw new DomainBadRequestException("error.skill.version.submit.notUploaded", version.getVersion());
        }

        // Validate version belongs to skill
        if (!version.getSkillId().equals(skillId)) {
            throw new DomainBadRequestException("error.skill.version.mismatch");
        }

        // Update version
        version.setStatus(SkillVersionStatus.PENDING_REVIEW);
        version.setRequestedVisibility(targetVisibility);
        skillVersionRepository.save(version);

        // Create review task
        ReviewTask reviewTask = new ReviewTask(versionId, skill.getNamespaceId(), actorUserId);
        reviewTaskRepository.save(reviewTask);
    }

    /**
     * Confirm publish for a PRIVATE skill version.
     * Transitions version status from UPLOADED/DRAFT to PUBLISHED without review.
     *
     * <p>Supports both UPLOADED (new flow) and DRAFT (legacy compatibility) status.
     *
     * @param skillId     the skill ID
     * @param versionId   the version ID
     * @param actorUserId the user performing the action
     * @param userNamespaceRoles user's namespace roles
     */
    @Transactional
    public void confirmPublish(Long skillId, Long versionId, String actorUserId,
                               Map<Long, NamespaceRole> userNamespaceRoles) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionId));

        // Validate ownership
        assertCanManageLifecycle(skill, actorUserId, userNamespaceRoles);

        // Validate skill visibility is PRIVATE
        if (skill.getVisibility() != SkillVisibility.PRIVATE) {
            throw new DomainBadRequestException("error.skill.confirm.notPrivate");
        }

        // Validate version status - support both UPLOADED (new) and DRAFT (legacy)
        if (version.getStatus() != SkillVersionStatus.UPLOADED
                && version.getStatus() != SkillVersionStatus.DRAFT) {
            throw new DomainBadRequestException("error.skill.version.confirm.notUploaded", version.getVersion());
        }

        // Validate version belongs to skill
        if (!version.getSkillId().equals(skillId)) {
            throw new DomainBadRequestException("error.skill.version.mismatch");
        }

        // Update version to PUBLISHED
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now(clock));
        skillVersionRepository.save(version);

        // Update skill's latest version
        skill.setLatestVersionId(versionId);
        skill.setUpdatedBy(actorUserId);
        skillRepository.save(skill);
    }

    private void assertCanManageLifecycle(Skill skill, String actorUserId, Map<Long, NamespaceRole> userNamespaceRoles) {
        NamespaceRole namespaceRole = userNamespaceRoles.get(skill.getNamespaceId());
        boolean canManage = skill.getOwnerId().equals(actorUserId)
                || namespaceRole == NamespaceRole.ADMIN
                || namespaceRole == NamespaceRole.OWNER;
        if (!canManage) {
            throw new DomainForbiddenException("error.skill.lifecycle.noPermission");
        }
    }
}
