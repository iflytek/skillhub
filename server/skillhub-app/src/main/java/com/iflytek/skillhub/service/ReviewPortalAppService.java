package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ReviewPortalAppService {

    private final ReviewService reviewService;
    private final ReviewTaskRepository reviewTaskRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final RbacService rbacService;
    private final AuditLogService auditLogService;

    public ReviewPortalAppService(ReviewService reviewService,
                                  ReviewTaskRepository reviewTaskRepository,
                                  SkillRepository skillRepository,
                                  SkillVersionRepository skillVersionRepository,
                                  NamespaceRepository namespaceRepository,
                                  UserAccountRepository userAccountRepository,
                                  RbacService rbacService,
                                  AuditLogService auditLogService) {
        this.reviewService = reviewService;
        this.reviewTaskRepository = reviewTaskRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.rbacService = rbacService;
        this.auditLogService = auditLogService;
    }

    public ReviewTaskResponse submitReview(Long skillVersionId,
                                           String userId,
                                           Map<Long, NamespaceRole> userNsRoles,
                                           AuditRequestContext auditContext) {
        ReviewTask task = reviewService.submitReview(
                skillVersionId,
                userId,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        recordAudit("REVIEW_SUBMIT", userId, task.getId(), auditContext, "{\"skillVersionId\":" + skillVersionId + "}");
        return toResponse(task);
    }

    public ReviewTaskResponse approveReview(Long reviewTaskId,
                                            String comment,
                                            String userId,
                                            Map<Long, NamespaceRole> userNsRoles,
                                            AuditRequestContext auditContext) {
        ReviewTask task = reviewService.approveReview(
                reviewTaskId,
                userId,
                comment,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        recordAudit("REVIEW_APPROVE", userId, task.getId(), auditContext, detailWithComment(comment));
        return toResponse(task);
    }

    public ReviewTaskResponse rejectReview(Long reviewTaskId,
                                           String comment,
                                           String userId,
                                           Map<Long, NamespaceRole> userNsRoles,
                                           AuditRequestContext auditContext) {
        ReviewTask task = reviewService.rejectReview(
                reviewTaskId,
                userId,
                comment,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        recordAudit("REVIEW_REJECT", userId, task.getId(), auditContext, detailWithComment(comment));
        return toResponse(task);
    }

    public void withdrawReview(Long reviewTaskId,
                               String userId,
                               AuditRequestContext auditContext) {
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewTaskId));
        reviewService.withdrawReview(task.getSkillVersionId(), userId);
        recordAudit(
                "REVIEW_WITHDRAW",
                userId,
                reviewTaskId,
                auditContext,
                "{\"skillVersionId\":" + task.getSkillVersionId() + "}"
        );
    }

    public PageResponse<ReviewTaskResponse> listReviews(String status,
                                                        Long namespaceId,
                                                        int page,
                                                        int size,
                                                        String userId,
                                                        Map<Long, NamespaceRole> userNsRoles) {
        ReviewTaskStatus reviewStatus = ReviewTaskStatus.valueOf(status.toUpperCase());
        Map<Long, NamespaceRole> namespaceRoles = normalizeRoles(userNsRoles);

        Page<ReviewTask> tasks;
        if (namespaceId != null) {
            Namespace namespace = namespaceRepository.findById(namespaceId)
                    .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceId));
            ReviewTask probe = new ReviewTask(0L, namespaceId, userId);
            if (!reviewService.canReviewNamespace(
                    probe,
                    userId,
                    namespace.getType(),
                    namespaceRoles,
                    platformRoles(userId))) {
                throw new DomainForbiddenException("review.no_permission");
            }
            tasks = reviewTaskRepository.findByNamespaceIdAndStatus(namespaceId, reviewStatus, PageRequest.of(page, size));
        } else {
            tasks = reviewTaskRepository.findByStatus(reviewStatus, PageRequest.of(page, size));
        }

        List<ReviewTaskResponse> visibleItems = tasks.getContent().stream()
                .filter(task -> canViewReview(task, userId, namespaceRoles))
                .map(this::toResponse)
                .toList();

        return PageResponse.from(new PageImpl<>(visibleItems, tasks.getPageable(), visibleItems.size()));
    }

    public PageResponse<ReviewTaskResponse> listPendingReviews(Long namespaceId,
                                                               int page,
                                                               int size,
                                                               String userId,
                                                               Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceId));
        ReviewTask probe = new ReviewTask(0L, namespaceId, userId);
        if (!reviewService.canReviewNamespace(
                probe,
                userId,
                namespace.getType(),
                normalizeRoles(userNsRoles),
                platformRoles(userId))) {
            throw new DomainForbiddenException("review.no_permission");
        }

        Page<ReviewTask> tasks = reviewTaskRepository.findByNamespaceIdAndStatus(
                namespaceId, ReviewTaskStatus.PENDING, PageRequest.of(page, size));
        return PageResponse.from(tasks.map(this::toResponse));
    }

    public PageResponse<ReviewTaskResponse> listMySubmissions(int page, int size, String userId) {
        Page<ReviewTask> tasks = reviewTaskRepository.findBySubmittedByAndStatus(
                userId, ReviewTaskStatus.PENDING, PageRequest.of(page, size));
        return PageResponse.from(tasks.map(this::toResponse));
    }

    public ReviewTaskResponse getReviewDetail(Long reviewTaskId,
                                              String userId,
                                              Map<Long, NamespaceRole> userNsRoles) {
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewTaskId));
        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        if (!reviewService.canViewReview(
                task,
                userId,
                namespace.getType(),
                normalizeRoles(userNsRoles),
                platformRoles(userId))) {
            throw new DomainForbiddenException("review.no_permission");
        }
        return toResponse(task);
    }

    private ReviewTaskResponse toResponse(ReviewTask task) {
        SkillVersion skillVersion = skillVersionRepository.findById(task.getSkillVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
        Skill skill = skillRepository.findById(skillVersion.getSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillVersion.getSkillId()));
        Namespace namespace = namespaceRepository.findById(skill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", skill.getNamespaceId()));

        String submittedByName = userAccountRepository.findById(task.getSubmittedBy())
                .map(UserAccount::getDisplayName)
                .orElse(null);
        String reviewedByName = task.getReviewedBy() != null
                ? userAccountRepository.findById(task.getReviewedBy()).map(UserAccount::getDisplayName).orElse(null)
                : null;

        return new ReviewTaskResponse(
                task.getId(),
                task.getSkillVersionId(),
                namespace.getSlug(),
                skill.getSlug(),
                skillVersion.getVersion(),
                task.getStatus().name(),
                task.getSubmittedBy(),
                submittedByName,
                task.getReviewedBy(),
                reviewedByName,
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt()
        );
    }

    private boolean canViewReview(ReviewTask task, String userId, Map<Long, NamespaceRole> namespaceRoles) {
        Namespace namespace = namespaceRepository.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        return reviewService.canViewReview(
                task,
                userId,
                namespace.getType(),
                namespaceRoles,
                platformRoles(userId)
        );
    }

    private Set<String> platformRoles(String userId) {
        return rbacService.getUserRoleCodes(userId);
    }

    private Map<Long, NamespaceRole> normalizeRoles(Map<Long, NamespaceRole> userNsRoles) {
        return userNsRoles != null ? userNsRoles : Map.of();
    }

    private void recordAudit(String action,
                             String userId,
                             Long targetId,
                             AuditRequestContext auditContext,
                             String detailJson) {
        auditLogService.record(
                userId,
                action,
                "REVIEW_TASK",
                targetId,
                MDC.get("requestId"),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }

    private String detailWithComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return "{\"comment\":\"" + comment.replace("\"", "\\\"") + "\"}";
    }
}
