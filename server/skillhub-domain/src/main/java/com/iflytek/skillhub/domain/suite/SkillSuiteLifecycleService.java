package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.Set;

/** Coordinates Suite version review and publication without changing any member lifecycle. */
@Service
public class SkillSuiteLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SkillSuiteLifecycleService.class);

    private final SkillSuiteRepository suiteRepository;
    private final SkillSuiteVersionRepository versionRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewPermissionChecker reviewPermissionChecker;
    private final NamespaceRepository namespaceRepository;
    private final SkillSuitePublicationValidator publicationValidator;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final boolean reviewWritesEnabled;

    public SkillSuiteLifecycleService(
            SkillSuiteRepository suiteRepository,
            SkillSuiteVersionRepository versionRepository,
            ReviewTaskRepository reviewTaskRepository,
            ReviewPermissionChecker reviewPermissionChecker,
            NamespaceRepository namespaceRepository,
            SkillSuitePublicationValidator publicationValidator,
            AuditLogService auditLogService,
            Clock clock,
            @Value("${skillhub.suite.review-writes-enabled}") boolean reviewWritesEnabled
    ) {
        this.suiteRepository = suiteRepository;
        this.versionRepository = versionRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewPermissionChecker = reviewPermissionChecker;
        this.namespaceRepository = namespaceRepository;
        this.publicationValidator = publicationValidator;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.reviewWritesEnabled = reviewWritesEnabled;
        if (!reviewWritesEnabled) {
            log.warn("Suite review writes are disabled for a mixed-version rolling upgrade");
        }
    }

    @Transactional
    public ReviewTask submitForReview(Long suiteId, Long versionId, SkillSuiteActionContext context) {
        if (!reviewWritesEnabled) {
            throw new DomainBadRequestException("error.suite.review.rolloutDisabled");
        }
        Loaded loaded = load(suiteId, versionId);
        assertNamespaceWritable(loaded.namespace());
        assertCanManageDraft(loaded.suite(), loaded.version(), context);
        if (loaded.version().getVisibility() == SkillVisibility.PRIVATE) {
            throw new DomainBadRequestException("error.suite.review.private");
        }
        if (loaded.version().getStatus() != SkillSuiteVersionStatus.DRAFT) {
            throw new DomainBadRequestException("error.suite.review.notDraft", loaded.version().getVersion());
        }
        publicationValidator.validate(loaded.suite(), loaded.version());

        loaded.version().setStatus(SkillSuiteVersionStatus.PENDING_REVIEW);
        versionRepository.save(loaded.version());
        ReviewTask task = reviewTaskRepository.save(ReviewTask.forSuiteVersion(
                versionId, suiteId, loaded.suite().getNamespaceId(),
                loaded.version().getVersion(), context.actorUserId()));
        audit(context, "SUBMIT_SKILL_SUITE_REVIEW", "SKILL_SUITE_VERSION", versionId, null);
        log.info("Suite review submitted [suiteId={}, versionId={}, actorId={}, requestId={}]",
                suiteId, versionId, context.actorUserId(), context.requestId());
        return task;
    }

    @Transactional
    public SkillSuiteVersion confirmPrivatePublish(
            Long suiteId,
            Long versionId,
            SkillSuiteActionContext context
    ) {
        Loaded loaded = load(suiteId, versionId);
        assertNamespaceWritable(loaded.namespace());
        assertCanManageDraft(loaded.suite(), loaded.version(), context);
        if (loaded.version().getVisibility() != SkillVisibility.PRIVATE) {
            throw new DomainBadRequestException("error.suite.publish.notPrivate");
        }
        if (loaded.version().getStatus() != SkillSuiteVersionStatus.DRAFT) {
            throw new DomainBadRequestException("error.suite.publish.notDraft", loaded.version().getVersion());
        }
        publicationValidator.validate(loaded.suite(), loaded.version());
        publish(loaded.suite(), loaded.version(), context.actorUserId());
        audit(context, "PUBLISH_SKILL_SUITE_VERSION", "SKILL_SUITE_VERSION", versionId, null);
        log.info("Private Suite published [suiteId={}, versionId={}, actorId={}, requestId={}]",
                suiteId, versionId, context.actorUserId(), context.requestId());
        return loaded.version();
    }

    @Transactional
    public ReviewTask approveReview(Long reviewTaskId, String comment, SkillSuiteActionContext context) {
        ReviewTask task = loadPendingSuiteReview(reviewTaskId);
        Loaded loaded = load(task.getSubjectId(), task.getSubjectVersionId());
        assertNamespaceWritable(loaded.namespace());
        assertCanReview(task, loaded.namespace(), context);
        if (loaded.version().getStatus() != SkillSuiteVersionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.suite.review.notPending", loaded.version().getVersion());
        }
        publicationValidator.validate(loaded.suite(), loaded.version());
        completeTask(task, ReviewTaskStatus.APPROVED, context.actorUserId(), comment);
        publish(loaded.suite(), loaded.version(), context.actorUserId());
        audit(context, "APPROVE_SKILL_SUITE_REVIEW", "REVIEW_TASK", reviewTaskId,
                AuditDetail.of("suiteVersionId", loaded.version().getId()));
        log.info("Suite review approved [reviewTaskId={}, suiteId={}, versionId={}, actorId={}, requestId={}]",
                reviewTaskId, loaded.suite().getId(), loaded.version().getId(),
                context.actorUserId(), context.requestId());
        return task;
    }

    @Transactional
    public ReviewTask rejectReview(Long reviewTaskId, String comment, SkillSuiteActionContext context) {
        ReviewTask task = loadPendingSuiteReview(reviewTaskId);
        Loaded loaded = load(task.getSubjectId(), task.getSubjectVersionId());
        assertNamespaceWritable(loaded.namespace());
        assertCanReview(task, loaded.namespace(), context);
        if (loaded.version().getStatus() != SkillSuiteVersionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.suite.review.notPending", loaded.version().getVersion());
        }
        completeTask(task, ReviewTaskStatus.REJECTED, context.actorUserId(), comment);
        loaded.version().setStatus(SkillSuiteVersionStatus.REJECTED);
        versionRepository.save(loaded.version());
        audit(context, "REJECT_SKILL_SUITE_REVIEW", "REVIEW_TASK", reviewTaskId,
                AuditDetail.of("suiteVersionId", loaded.version().getId()));
        log.info("Suite review rejected [reviewTaskId={}, suiteId={}, versionId={}, actorId={}, requestId={}]",
                reviewTaskId, loaded.suite().getId(), loaded.version().getId(),
                context.actorUserId(), context.requestId());
        return task;
    }

    @Transactional
    public void withdrawReview(Long reviewTaskId, SkillSuiteActionContext context) {
        ReviewTask task = loadPendingSuiteReview(reviewTaskId);
        Loaded loaded = load(task.getSubjectId(), task.getSubjectVersionId());
        assertCanManageDraft(loaded.suite(), loaded.version(), context);
        if (loaded.version().getStatus() != SkillSuiteVersionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.suite.review.notPending", loaded.version().getVersion());
        }
        loaded.version().setStatus(SkillSuiteVersionStatus.DRAFT);
        versionRepository.save(loaded.version());
        reviewTaskRepository.delete(task);
        audit(context, "WITHDRAW_SKILL_SUITE_REVIEW", "SKILL_SUITE_VERSION",
                loaded.version().getId(), null);
        log.info("Suite review withdrawn [reviewTaskId={}, suiteId={}, versionId={}, actorId={}, requestId={}]",
                reviewTaskId, loaded.suite().getId(), loaded.version().getId(),
                context.actorUserId(), context.requestId());
    }

    @Transactional
    public SkillSuiteVersion reopenRejected(Long suiteId, Long versionId, SkillSuiteActionContext context) {
        Loaded loaded = load(suiteId, versionId);
        assertNamespaceWritable(loaded.namespace());
        assertCanManageDraft(loaded.suite(), loaded.version(), context);
        if (loaded.version().getStatus() != SkillSuiteVersionStatus.REJECTED) {
            throw new DomainBadRequestException("error.suite.review.notRejected", loaded.version().getVersion());
        }
        loaded.version().setStatus(SkillSuiteVersionStatus.DRAFT);
        versionRepository.save(loaded.version());
        audit(context, "REOPEN_SKILL_SUITE_VERSION", "SKILL_SUITE_VERSION", versionId, null);
        log.info("Rejected Suite reopened [suiteId={}, versionId={}, actorId={}, requestId={}]",
                suiteId, versionId, context.actorUserId(), context.requestId());
        return loaded.version();
    }

    @Transactional
    public SkillSuiteVersion yank(Long suiteId, Long versionId, String reason, SkillSuiteActionContext context) {
        Loaded loaded = load(suiteId, versionId);
        assertCanAdminister(loaded.suite(), context);
        if (loaded.version().getStatus() != SkillSuiteVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.suite.version.notPublished", loaded.version().getVersion());
        }
        loaded.version().setStatus(SkillSuiteVersionStatus.YANKED);
        loaded.version().setYankedAt(Instant.now(clock));
        loaded.version().setYankedBy(context.actorUserId());
        loaded.version().setYankReason(reason);
        versionRepository.save(loaded.version());
        if (versionId.equals(loaded.suite().getLatestVersionId())) {
            SkillSuiteVersion latest = versionRepository
                    .findBySuiteIdAndStatus(suiteId, SkillSuiteVersionStatus.PUBLISHED)
                    .stream()
                    .max(Comparator.comparing(SkillSuiteVersion::getPublishedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            loaded.suite().setLatestVersionId(latest == null ? null : latest.getId());
            if (latest != null) {
                loaded.suite().setDisplayName(latest.getDisplayName());
                loaded.suite().setSummary(latest.getSummary());
            }
            loaded.suite().setUpdatedBy(context.actorUserId());
            suiteRepository.save(loaded.suite());
        }
        audit(context, "YANK_SKILL_SUITE_VERSION", "SKILL_SUITE_VERSION", versionId,
                AuditDetail.of("reason", reason));
        log.info("Suite version yanked [suiteId={}, versionId={}, actorId={}, requestId={}]",
                suiteId, versionId, context.actorUserId(), context.requestId());
        return loaded.version();
    }

    @Transactional
    public SkillSuite setHidden(Long suiteId, boolean hidden, SkillSuiteActionContext context) {
        SkillSuite suite = loadSuite(suiteId);
        assertCanAdminister(suite, context);
        if (suite.isHidden() == hidden) {
            return suite;
        }
        suite.setHidden(hidden);
        suite.setHiddenAt(hidden ? Instant.now(clock) : null);
        suite.setHiddenBy(hidden ? context.actorUserId() : null);
        suite.setUpdatedBy(context.actorUserId());
        suiteRepository.save(suite);
        String action = hidden ? "HIDE_SKILL_SUITE" : "RESTORE_SKILL_SUITE";
        audit(context, action, "SKILL_SUITE", suiteId, null);
        log.info("Suite visibility overlay changed [suiteId={}, hidden={}, actorId={}, requestId={}]",
                suiteId, hidden, context.actorUserId(), context.requestId());
        return suite;
    }

    @Transactional
    public SkillSuite setArchived(Long suiteId, boolean archived, SkillSuiteActionContext context) {
        SkillSuite suite = loadSuite(suiteId);
        assertCanAdminister(suite, context);
        SkillSuiteStatus target = archived ? SkillSuiteStatus.ARCHIVED : SkillSuiteStatus.ACTIVE;
        if (suite.getStatus() == target) {
            return suite;
        }
        suite.setStatus(target);
        suite.setUpdatedBy(context.actorUserId());
        suiteRepository.save(suite);
        String action = archived ? "ARCHIVE_SKILL_SUITE" : "UNARCHIVE_SKILL_SUITE";
        audit(context, action, "SKILL_SUITE", suiteId, null);
        log.info("Suite container status changed [suiteId={}, status={}, actorId={}, requestId={}]",
                suiteId, target, context.actorUserId(), context.requestId());
        return suite;
    }

    @Transactional
    public void delete(Long suiteId, SkillSuiteActionContext context) {
        SkillSuite suite = loadSuite(suiteId);
        assertCanAdminister(suite, context);
        boolean pendingReview = versionRepository.findBySuiteId(suiteId).stream()
                .anyMatch(version -> version.getStatus() == SkillSuiteVersionStatus.PENDING_REVIEW);
        if (pendingReview) {
            throw new DomainBadRequestException("error.suite.delete.pendingReview");
        }
        audit(context, "DELETE_SKILL_SUITE", "SKILL_SUITE", suiteId,
                AuditDetail.of("slug", suite.getSlug()));
        // Review tasks are lifecycle-owned by the hard-deleted Suite. Keeping them would leave
        // polymorphic subject IDs that can no longer be resolved by governance read models.
        reviewTaskRepository.deleteBySubjectTypeAndSubjectId(ReviewSubjectType.SUITE_VERSION, suiteId);
        suiteRepository.delete(suite);
        log.info("Suite deleted [suiteId={}, namespaceId={}, actorId={}, requestId={}]",
                suiteId, suite.getNamespaceId(), context.actorUserId(), context.requestId());
    }

    /**
     * Returns state-aware actions for display. Command methods still authorize independently so a
     * cached response cannot grant access after roles or lifecycle state change.
     */
    @Transactional(readOnly = true)
    public Set<SkillSuiteAllowedAction> allowedActions(
            SkillSuite suite,
            SkillSuiteVersion version,
            Namespace namespace,
            SkillSuiteActionContext context
    ) {
        EnumSet<SkillSuiteAllowedAction> actions = EnumSet.noneOf(SkillSuiteAllowedAction.class);
        boolean namespaceWritable = namespace.getStatus() == NamespaceStatus.ACTIVE;
        boolean canManageVersion = SkillSuiteAuthorizationPolicy.canManageVersion(suite, version, context);
        boolean canCreateVersion = SkillSuiteAuthorizationPolicy.canCreateVersion(suite, context);
        boolean canAdminister = SkillSuiteAuthorizationPolicy.canAdminister(suite, context);

        if (namespaceWritable && canManageVersion) {
            if (version.getStatus() == SkillSuiteVersionStatus.DRAFT) {
                actions.add(SkillSuiteAllowedAction.EDIT);
                if (version.getVisibility() == SkillVisibility.PRIVATE) {
                    actions.add(SkillSuiteAllowedAction.PUBLISH_PRIVATE);
                } else if (reviewWritesEnabled) {
                    actions.add(SkillSuiteAllowedAction.SUBMIT);
                }
            } else if (version.getStatus() == SkillSuiteVersionStatus.REJECTED) {
                actions.add(SkillSuiteAllowedAction.REOPEN);
            }
        }
        if (namespaceWritable
                && suite.getStatus() == SkillSuiteStatus.ACTIVE
                && canCreateVersion
                && (version.getStatus() == SkillSuiteVersionStatus.PUBLISHED
                || version.getStatus() == SkillSuiteVersionStatus.YANKED)) {
            actions.add(SkillSuiteAllowedAction.CREATE_VERSION);
        }
        if (canAdminister) {
            if (version.getStatus() == SkillSuiteVersionStatus.PUBLISHED) {
                actions.add(SkillSuiteAllowedAction.YANK);
            }
            actions.add(suite.isHidden()
                    ? SkillSuiteAllowedAction.RESTORE
                    : SkillSuiteAllowedAction.HIDE);
            actions.add(suite.getStatus() == SkillSuiteStatus.ARCHIVED
                    ? SkillSuiteAllowedAction.UNARCHIVE
                    : SkillSuiteAllowedAction.ARCHIVE);
            boolean pendingReview = versionRepository.findBySuiteIdAndStatus(
                    suite.getId(), SkillSuiteVersionStatus.PENDING_REVIEW).stream().findAny().isPresent();
            if (!pendingReview) {
                actions.add(SkillSuiteAllowedAction.DELETE);
            }
        }
        return Set.copyOf(actions);
    }

    private SkillSuite loadSuite(Long suiteId) {
        return suiteRepository.findById(suiteId)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteId));
    }

    private Loaded load(Long suiteId, Long versionId) {
        SkillSuite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteId));
        SkillSuiteVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.version.notFound", versionId));
        if (!suiteId.equals(version.getSuiteId())) {
            throw new DomainBadRequestException("error.suite.version.mismatch");
        }
        Namespace namespace = namespaceRepository.findById(suite.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", suite.getNamespaceId()));
        return new Loaded(suite, version, namespace);
    }

    private void publish(SkillSuite suite, SkillSuiteVersion version, String actorUserId) {
        version.setStatus(SkillSuiteVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now(clock));
        versionRepository.save(version);
        suite.setLatestVersionId(version.getId());
        // The container is the searchable projection of the latest published immutable version.
        suite.setDisplayName(version.getDisplayName());
        suite.setSummary(version.getSummary());
        suite.setUpdatedBy(actorUserId);
        suiteRepository.save(suite);
    }

    private ReviewTask loadPendingSuiteReview(Long reviewTaskId) {
        ReviewTask task = reviewTaskRepository.findById(reviewTaskId)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", reviewTaskId));
        if (task.getSubjectType() != ReviewSubjectType.SUITE_VERSION) {
            throw new DomainBadRequestException("error.suite.review.subjectMismatch");
        }
        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("review.not_pending", reviewTaskId);
        }
        return task;
    }

    private void completeTask(ReviewTask task, ReviewTaskStatus status, String reviewerId, String comment) {
        int updated = reviewTaskRepository.updateStatusWithVersion(
                task.getId(), status, reviewerId, comment, task.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Review task was modified concurrently");
        }
        // The bulk update is the concurrency boundary. Keep this detached return value aligned
        // with the committed decision without leaking persistence mechanics into the domain layer.
        task.setStatus(status);
        task.setReviewedBy(reviewerId);
        task.setReviewComment(comment);
        task.setReviewedAt(Instant.now(clock));
    }

    private void assertNamespaceWritable(Namespace namespace) {
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("error.suite.namespace.notWritable", namespace.getStatus());
        }
    }

    private void assertCanManageDraft(
            SkillSuite suite,
            SkillSuiteVersion version,
            SkillSuiteActionContext context
    ) {
        if (!SkillSuiteAuthorizationPolicy.canManageVersion(suite, version, context)) {
            throw new DomainForbiddenException("error.suite.lifecycle.noPermission");
        }
    }

    private void assertCanAdminister(SkillSuite suite, SkillSuiteActionContext context) {
        if (!SkillSuiteAuthorizationPolicy.canAdminister(suite, context)) {
            throw new DomainForbiddenException("error.suite.lifecycle.noPermission");
        }
    }

    private void assertCanReview(ReviewTask task, Namespace namespace, SkillSuiteActionContext context) {
        if (!reviewPermissionChecker.canReview(
                task,
                context.actorUserId(),
                namespace.getType(),
                context.namespaceRoles(),
                context.platformRoles())) {
            throw new DomainForbiddenException("review.no_permission");
        }
    }

    private void audit(
            SkillSuiteActionContext context,
            String action,
            String targetType,
            Long targetId,
            String detail
    ) {
        auditLogService.record(
                context.actorUserId(), action, targetType, targetId, context.requestId(),
                context.clientIp(), context.userAgent(), detail);
    }

    private record Loaded(SkillSuite suite, SkillSuiteVersion version, Namespace namespace) {
    }
}
