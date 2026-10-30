package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.event.ReviewApprovedEvent;
import com.iflytek.skillhub.domain.event.ReviewRejectedEvent;
import com.iflytek.skillhub.domain.event.ReviewSubmittedEvent;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates the review workflow for skill versions.
 *
 * <p>This service owns transitions between draft, pending review, approved,
 * and rejected states together with the review task record that tracks the
 * moderation decision.
 */
@Service
public class ReviewService {
    private final SkillOwnerAccessPolicy ownerAccess;

    private final ReviewTaskRepository reviewTaskRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final ReviewPermissionChecker permissionChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final SkillGovernanceService skillGovernanceService;
    private final GovernanceNotificationService governanceNotificationService;
    private final EntityManager entityManager;
    private final Clock clock;
    private ReviewAvatarParticipant avatarParticipant;
    private com.iflytek.skillhub.domain.skill.service.SkillVisibilityService visibilityService;

    @Autowired
    public void setVisibilityService(com.iflytek.skillhub.domain.skill.service.SkillVisibilityService service) {
        this.visibilityService = service;
    }


    @Autowired
    public void setAvatarParticipant(ReviewAvatarParticipant participant) { this.avatarParticipant = participant; }


    public ReviewService(SkillOwnerAccessPolicy ownerAccess, ReviewTaskRepository reviewTaskRepository,
                         SkillVersionRepository skillVersionRepository,
                         SkillRepository skillRepository,
                         NamespaceRepository namespaceRepository,
                         ReviewPermissionChecker permissionChecker,
                         ApplicationEventPublisher eventPublisher,
                         ObjectMapper objectMapper,
                         SkillGovernanceService skillGovernanceService,
                         GovernanceNotificationService governanceNotificationService,
                         EntityManager entityManager,
                         Clock clock) {
        this.ownerAccess = ownerAccess;
        this.reviewTaskRepository = reviewTaskRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.permissionChecker = permissionChecker;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.skillGovernanceService = skillGovernanceService;
        this.governanceNotificationService = governanceNotificationService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Submits a draft version into the review queue using both namespace roles
     * and platform roles to determine permission.
     */
    @Transactional
    public ReviewTask submitReview(Long skillVersionId,
                                   String userId,
                                   Map<Long, NamespaceRole> userNamespaceRoles,
                                   Set<String> platformRoles) {
        SkillVersion skillVersion = skillVersionRepository.findById(skillVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", skillVersionId));
        if (skillVersion.getGithubSyncIngestionId() != null) {
            throw new DomainBadRequestException("review.submit.githubSyncManaged", skillVersionId);
        }

        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));
        if (skill.isGithubSyncManaged()) {
            throw new DomainBadRequestException("review.submit.githubSyncManaged", skillVersionId);
        }
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", skill.getNamespaceId()));
        assertNamespaceActive(namespace);
        if (!platformRoles.contains("SKILL_ADMIN")) {
            ownerAccess.assertCanWrite(skill, userId, platformRoles);
        }

        if (!permissionChecker.canSubmitForReview(skill, userId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.submit.no_permission");
        }

        if (visibilityService != null) {
            visibilityService.lockedSkill(skill.getId());
            visibilityService.assertNoPendingVisibility(skill.getId());
        }
        // Support both DRAFT (legacy) and UPLOADED (new flow) status
        if (skillVersion.getStatus() != SkillVersionStatus.DRAFT
                && skillVersion.getStatus() != SkillVersionStatus.UPLOADED) {
            throw new DomainBadRequestException("review.submit.not_draft", skillVersionId);
        }

        skillVersion.setStatus(SkillVersionStatus.PENDING_REVIEW);
        skillVersionRepository.save(skillVersion);

        ReviewTask task = new ReviewTask(
                skillVersionId, skill.getId(), skill.getNamespaceId(), skillVersion.getVersion(), userId);
        try {
            ReviewTask saved = reviewTaskRepository.save(task);
            eventPublisher.publishEvent(new ReviewSubmittedEvent(
                    saved.getId(), skillVersion.getSkillId(), skillVersion.getId(),
                    saved.getSubmittedBy(), saved.getNamespaceId()));
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new DomainBadRequestException("review.submit.duplicate", skillVersionId);
        }
    }

    /**
     * Legacy overload that evaluates submission rights only from namespace
     * memberships.
     */
    @Transactional
    public ReviewTask submitReview(Long skillVersionId,
                                   String userId,
                                   Map<Long, NamespaceRole> userNamespaceRoles) {
        SkillVersion skillVersion = skillVersionRepository.findById(skillVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", skillVersionId));
        if (skillVersion.getGithubSyncIngestionId() != null) {
            throw new DomainBadRequestException("review.submit.githubSyncManaged", skillVersionId);
        }

        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));
        if (skill.isGithubSyncManaged()) {
            throw new DomainBadRequestException("review.submit.githubSyncManaged", skillVersionId);
        }
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", skill.getNamespaceId()));
        assertNamespaceActive(namespace);
        ownerAccess.assertCanWrite(skill, userId, Set.of());

        if (visibilityService != null) {
            visibilityService.lockedSkill(skill.getId());
            visibilityService.assertNoPendingVisibility(skill.getId());
        }
        // Support both DRAFT (legacy) and UPLOADED (new flow) status
        if (skillVersion.getStatus() != SkillVersionStatus.DRAFT
                && skillVersion.getStatus() != SkillVersionStatus.UPLOADED) {
            throw new DomainBadRequestException("review.submit.not_draft", skillVersionId);
        }

        if (!permissionChecker.canSubmitReview(skill.getNamespaceId(), userNamespaceRoles)) {
            throw new DomainForbiddenException("review.submit.no_permission");
        }

        skillVersion.setStatus(SkillVersionStatus.PENDING_REVIEW);
        skillVersionRepository.save(skillVersion);

        ReviewTask task = new ReviewTask(
                skillVersionId, skill.getId(), skill.getNamespaceId(), skillVersion.getVersion(), userId);
        try {
            ReviewTask saved = reviewTaskRepository.save(task);
            eventPublisher.publishEvent(new ReviewSubmittedEvent(
                    saved.getId(), skillVersion.getSkillId(), skillVersion.getId(),
                    saved.getSubmittedBy(), saved.getNamespaceId()));
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new DomainBadRequestException("review.submit.duplicate", skillVersionId);
        }
    }

    /**
     * Approves a pending review task, publishes the underlying version, and
     * emits downstream notifications and publication events.
     */
    @Transactional
    public ReviewTask approveReview(Long reviewTaskId, String reviewerId, String comment,
                                    Map<Long, NamespaceRole> userNamespaceRoles,
                                    Set<String> platformRoles) {
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewTaskId));

        if (avatarParticipant != null) {
            avatarParticipant.lock(task);
            entityManager.refresh(task);
        }

        if (avatarParticipant == null && (task.getAvatarCandidateId() != null || task.isAvatarRemoval()
                || task.getSubjectType() == ReviewSubjectType.SKILL_METADATA)) {
            throw new IllegalStateException("Avatar review participant unavailable");
        }
        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("review.not_pending", reviewTaskId);
        }

        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        assertNamespaceActive(namespace);

        if (!permissionChecker.canReview(task, reviewerId, namespace.getType(),
                userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.no_permission");
        }

        if (task.getRequestedVisibility() != null) { visibilityService.validateApproval(task); }
        if (avatarParticipant != null) { avatarParticipant.validateApproval(task, comment); }
        if (task.getSubjectType() == ReviewSubjectType.SKILL_METADATA) {
            return decideMetadata(task, reviewerId, comment, true);
        }
        SkillVersion skillVersion = skillVersionRepository.findById(task.getSkillVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
        if (skillVersion.getStatus() == SkillVersionStatus.SCANNING) {
            throw new DomainBadRequestException("review.approve.scan_in_progress", reviewTaskId);
        }

        int updated = reviewTaskRepository.updateStatusWithVersion(
                reviewTaskId, ReviewTaskStatus.APPROVED, reviewerId, comment, task.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Review task was modified concurrently");
        }
        syncReviewTaskState(task, ReviewTaskStatus.APPROVED, reviewerId, comment);
        entityManager.detach(task);

        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));

        // Check no other owner has a published skill with the same slug
        List<Skill> sameSlugSkills = skillRepository.findByNamespaceIdAndSlug(skill.getNamespaceId(), skill.getSlug());
        for (Skill other : sameSlugSkills) {
            if (!other.getId().equals(skill.getId())) {
                boolean otherHasPublished = !skillVersionRepository
                        .findBySkillIdAndStatus(other.getId(), SkillVersionStatus.PUBLISHED)
                        .isEmpty();
                if (otherHasPublished) {
                    throw new DomainBadRequestException("error.skill.approve.nameConflict", skill.getSlug());
                }
            }
        }

        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        skillVersion.setPublishedAt(currentTime());
        skillVersionRepository.save(skillVersion);

        skill.setLatestVersionId(skillVersion.getId());
        if (skillVersion.getRequestedVisibility() != null) {
            skill.setVisibility(skillVersion.getRequestedVisibility());
        }
        applyPublishedMetadata(skill, skillVersion);
        skill.setUpdatedBy(reviewerId);
        skillRepository.save(skill);

        if (avatarParticipant != null) { avatarParticipant.decide(task, true, reviewerId, comment); }
        eventPublisher.publishEvent(new SkillPublishedEvent(
                skill.getId(), skillVersion.getId(), reviewerId));
        eventPublisher.publishEvent(new ReviewApprovedEvent(
                task.getId(), skill.getId(), skillVersion.getId(),
                reviewerId, task.getSubmittedBy()));
        governanceNotificationService.notifyUser(
                task.getSubmittedBy(),
                "REVIEW",
                "REVIEW_TASK",
                reviewTaskId,
                "Review approved",
                AuditDetail.of("status", "APPROVED")
        );

        return task;
    }

    /**
     * Rejects a pending review task and returns the underlying version to a
     * non-published state with reviewer metadata captured on the task.
     */
    @Transactional
    public ReviewTask rejectReview(Long reviewTaskId, String reviewerId, String comment,
                                   Map<Long, NamespaceRole> userNamespaceRoles,
                                   Set<String> platformRoles) {
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewTaskId));

        if (avatarParticipant != null) {
            avatarParticipant.lock(task);
            entityManager.refresh(task);
        }

        if (avatarParticipant == null && (task.getAvatarCandidateId() != null || task.isAvatarRemoval()
                || task.getSubjectType() == ReviewSubjectType.SKILL_METADATA)) {
            throw new IllegalStateException("Avatar review participant unavailable");
        }
        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("review.not_pending", reviewTaskId);
        }

        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        assertNamespaceActive(namespace);

        if (!permissionChecker.canReview(task, reviewerId, namespace.getType(),
                userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.no_permission");
        }

        if (task.getSubjectType() == ReviewSubjectType.SKILL_METADATA) {
            return decideMetadata(task, reviewerId, comment, false);
        }
        int updated = reviewTaskRepository.updateStatusWithVersion(
                reviewTaskId, ReviewTaskStatus.REJECTED, reviewerId, comment, task.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Review task was modified concurrently");
        }
        syncReviewTaskState(task, ReviewTaskStatus.REJECTED, reviewerId, comment);
        entityManager.detach(task);

        SkillVersion skillVersion = skillVersionRepository.findById(task.getSkillVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));
        skillVersion.setStatus(SkillVersionStatus.REJECTED);
        skillVersionRepository.save(skillVersion);
        if (avatarParticipant != null) { avatarParticipant.decide(task, false, reviewerId, comment); }
        eventPublisher.publishEvent(new ReviewRejectedEvent(
                task.getId(), skill.getId(), skillVersion.getId(),
                reviewerId, task.getSubmittedBy(), comment));
        governanceNotificationService.notifyUser(
                task.getSubmittedBy(),
                "REVIEW",
                "REVIEW_TASK",
                reviewTaskId,
                "Review rejected",
                AuditDetail.of("status", "REJECTED")
        );

        return task;
    }

    /**
     * Withdraws a previously submitted review request and puts the version back
     * into draft so the owner can amend and resubmit it.
     */
    @Transactional
    public SkillVersion withdrawReview(Long skillVersionId, String userId) {
        return withdrawReview(skillVersionId, userId, java.util.Set.of());
    }

    @Transactional
    public SkillVersion withdrawReview(Long skillVersionId, String userId, java.util.Set<String> platformRoles) {
        ReviewTask task = reviewTaskRepository.findBySkillVersionIdAndStatus(
                        skillVersionId, ReviewTaskStatus.PENDING)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found_for_version", skillVersionId));

        if (!task.getSubmittedBy().equals(userId)) {
            throw new DomainForbiddenException("review.withdraw.not_submitter");
        }

        Skill withdrawnSkill = skillRepository.findById(task.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", task.getSkillId()));
        ownerAccess.assertCanWrite(withdrawnSkill, userId, platformRoles);

        if (avatarParticipant != null) { avatarParticipant.lock(task); avatarParticipant.withdraw(task); }
        reviewTaskRepository.delete(task);

        SkillVersion skillVersion = skillVersionRepository.findById(skillVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", skillVersionId));
        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", skill.getNamespaceId()));
        assertNamespaceActive(namespace);
        return skillGovernanceService.withdrawPendingVersion(skill, skillVersion, userId);
    }

    private ReviewTask decideMetadata(ReviewTask task, String reviewer, String comment, boolean approved) {
        if (avatarParticipant == null) { throw new IllegalStateException("Avatar review participant unavailable"); }
        ReviewTaskStatus status = approved ? ReviewTaskStatus.APPROVED : ReviewTaskStatus.REJECTED;
        if (reviewTaskRepository.updateStatusWithVersion(task.getId(), status, reviewer, comment, task.getVersion()) == 0) {
            throw new ConcurrentModificationException("Review task was modified concurrently");
        }
        syncReviewTaskState(task, status, reviewer, comment);
        if (task.getRequestedVisibility() != null && approved) { visibilityService.approve(task, reviewer); }
        avatarParticipant.decide(task, approved, reviewer, comment);
        governanceNotificationService.notifyUser(task.getSubmittedBy(), "REVIEW", "REVIEW_TASK", task.getId(),
                approved ? "Review approved" : "Review rejected", AuditDetail.of("status", status.name()));
        return task;
    }

    @Transactional
    public void withdrawMetadata(ReviewTask task, String userId) {
        withdrawMetadata(task, userId, java.util.Set.of());
    }

    @Transactional
    public void withdrawMetadata(ReviewTask task, String userId, java.util.Set<String> platformRoles) {
        if (!task.getSubmittedBy().equals(userId)) { throw new DomainForbiddenException("review.withdraw.not_submitter"); }
        Skill skill = skillRepository.findById(task.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", task.getSkillId()));
        ownerAccess.assertCanWrite(skill, userId, platformRoles);
        if (avatarParticipant != null) { avatarParticipant.lock(task); entityManager.refresh(task); }
        if (avatarParticipant == null && (task.getAvatarCandidateId() != null || task.isAvatarRemoval()
                || task.getSubjectType() == ReviewSubjectType.SKILL_METADATA)) {
            throw new IllegalStateException("Avatar review participant unavailable");
        }
        if (task.getStatus() != ReviewTaskStatus.PENDING) { throw new DomainBadRequestException("review.not_pending", task.getId()); }
        avatarParticipant.withdraw(task);
        reviewTaskRepository.delete(task);
    }

    public boolean canReviewNamespace(ReviewTask task,
                                      String userId,
                                      com.iflytek.skillhub.domain.namespace.NamespaceType namespaceType,
                                      Map<Long, NamespaceRole> userNamespaceRoles,
                                      Set<String> platformRoles) {
        return permissionChecker.canReviewNamespace(task.getNamespaceId(), namespaceType, userNamespaceRoles, platformRoles);
    }

    public boolean canViewReview(ReviewTask task,
                                 String userId,
                                 com.iflytek.skillhub.domain.namespace.NamespaceType namespaceType,
                                 Map<Long, NamespaceRole> userNamespaceRoles,
                                 Set<String> platformRoles) {
        return permissionChecker.canViewReview(task, userId, namespaceType, userNamespaceRoles, platformRoles);
    }

    private void applyPublishedMetadata(Skill skill, SkillVersion skillVersion) {
        String metadataJson = skillVersion.getParsedMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return;
        }

        try {
            SkillMetadata metadata = objectMapper.readValue(metadataJson, SkillMetadata.class);
            skill.setDisplayName(metadata.displayName());
            skill.setSummary(metadata.shortDescription());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize skill metadata", e);
        }
    }

    private void assertNamespaceActive(Namespace namespace) {
        if (namespace.getStatus() == NamespaceStatus.FROZEN) {
            throw new DomainBadRequestException("error.namespace.frozen", namespace.getSlug());
        }
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED) {
            throw new DomainBadRequestException("error.namespace.archived", namespace.getSlug());
        }
    }

    private Instant currentTime() {
        return Instant.now(clock);
    }

    private void syncReviewTaskState(ReviewTask task,
                                     ReviewTaskStatus status,
                                     String reviewedBy,
                                     String comment) {
        task.setStatus(status);
        task.setReviewedBy(reviewedBy);
        task.setReviewComment(comment);
        task.setReviewedAt(currentTime());
    }
}
