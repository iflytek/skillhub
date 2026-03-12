package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final ReviewPermissionChecker permissionChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public ReviewService(ReviewTaskRepository reviewTaskRepository,
                         SkillVersionRepository skillVersionRepository,
                         SkillRepository skillRepository,
                         NamespaceRepository namespaceRepository,
                         ReviewPermissionChecker permissionChecker,
                         ApplicationEventPublisher eventPublisher,
                         EntityManager entityManager) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.permissionChecker = permissionChecker;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @Transactional
    public ReviewTask submitReview(Long skillVersionId,
                                   String userId,
                                   Map<Long, NamespaceRole> userNamespaceRoles,
                                   Set<String> platformRoles) {
        SkillVersion skillVersion = skillVersionRepository.findById(skillVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", skillVersionId));
        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));

        if (!permissionChecker.canSubmitForReview(skill, userId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.submit.no_permission");
        }

        if (skillVersion.getStatus() != SkillVersionStatus.DRAFT) {
            throw new DomainBadRequestException("review.submit.not_draft", skillVersionId);
        }

        skillVersion.setStatus(SkillVersionStatus.PENDING_REVIEW);
        skillVersionRepository.save(skillVersion);

        ReviewTask task = new ReviewTask(skillVersionId, skill.getNamespaceId(), userId);
        try {
            return reviewTaskRepository.save(task);
        } catch (DataIntegrityViolationException e) {
            throw new DomainBadRequestException("review.submit.duplicate", skillVersionId);
        }
    }

    @Transactional
    public ReviewTask approveReview(Long reviewTaskId, String reviewerId, String comment,
                                    Map<Long, NamespaceRole> userNamespaceRoles,
                                    Set<String> platformRoles) {
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewTaskId));

        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("review.not_pending", reviewTaskId);
        }

        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));

        if (!permissionChecker.canReview(task, reviewerId, namespace.getType(),
                userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.no_permission");
        }

        int updated = reviewTaskRepository.updateStatusWithVersion(
                reviewTaskId, ReviewTaskStatus.APPROVED, reviewerId, comment, task.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Review task was modified concurrently");
        }
        entityManager.detach(task);
        task.setStatus(ReviewTaskStatus.APPROVED);
        task.setReviewedBy(reviewerId);
        task.setReviewComment(comment);
        task.setReviewedAt(Instant.now());

        SkillVersion skillVersion = skillVersionRepository.findById(task.getSkillVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
        skillVersion.setStatus(SkillVersionStatus.PUBLISHED);
        skillVersion.setPublishedAt(LocalDateTime.now());
        skillVersionRepository.save(skillVersion);

        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));
        skill.setLatestVersionId(skillVersion.getId());
        skillRepository.save(skill);

        eventPublisher.publishEvent(new SkillPublishedEvent(
                skill.getId(), skillVersion.getId(), reviewerId));

        return task;
    }

    @Transactional
    public ReviewTask rejectReview(Long reviewTaskId, String reviewerId, String comment,
                                   Map<Long, NamespaceRole> userNamespaceRoles,
                                   Set<String> platformRoles) {
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewTaskId));

        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("review.not_pending", reviewTaskId);
        }

        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));

        if (!permissionChecker.canReview(task, reviewerId, namespace.getType(),
                userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("review.no_permission");
        }

        int updated = reviewTaskRepository.updateStatusWithVersion(
                reviewTaskId, ReviewTaskStatus.REJECTED, reviewerId, comment, task.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Review task was modified concurrently");
        }
        entityManager.detach(task);
        task.setStatus(ReviewTaskStatus.REJECTED);
        task.setReviewedBy(reviewerId);
        task.setReviewComment(comment);
        task.setReviewedAt(Instant.now());

        SkillVersion skillVersion = skillVersionRepository.findById(task.getSkillVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
        skillVersion.setStatus(SkillVersionStatus.REJECTED);
        skillVersionRepository.save(skillVersion);

        return task;
    }

    @Transactional
    public void withdrawReview(Long skillVersionId, String userId) {
        ReviewTask task = reviewTaskRepository.findBySkillVersionIdAndStatus(
                        skillVersionId, ReviewTaskStatus.PENDING)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found_for_version", skillVersionId));

        if (!task.getSubmittedBy().equals(userId)) {
            throw new DomainForbiddenException("review.withdraw.not_submitter");
        }

        reviewTaskRepository.delete(task);

        SkillVersion skillVersion = skillVersionRepository.findById(skillVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", skillVersionId));
        skillVersion.setStatus(SkillVersionStatus.DRAFT);
        skillVersionRepository.save(skillVersion);
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
}
