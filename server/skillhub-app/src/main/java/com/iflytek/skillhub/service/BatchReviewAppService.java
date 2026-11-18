package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.review.*;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.dto.BatchReviewRequest;
import com.iflytek.skillhub.dto.BatchReviewResponse;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.ConcurrentModificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded orchestration; each existing review decision commits or rolls back independently. */
@Service
public class BatchReviewAppService {
    private static final Logger log = LoggerFactory.getLogger(BatchReviewAppService.class);
    private final ReviewPortalAppService portal;
    private final ReviewTaskRepository reviews;
    private final SkillRepository skills;
    private final SkillVersionRepository versions;
    private final SecurityAuditRepository audits;
    private final NamespaceRepository namespaces;
    private final NamespaceMemberRepository members;
    private final ReviewPermissionChecker permissions;
    private final RbacService rbac;
    private final EntityManager entityManager;
    private final MessageSource messages;
    private final TransactionTemplate transactions;

    public BatchReviewAppService(ReviewPortalAppService portal, ReviewTaskRepository reviews,
            SkillRepository skills, SkillVersionRepository versions, SecurityAuditRepository audits,
            NamespaceRepository namespaces, NamespaceMemberRepository members,
            ReviewPermissionChecker permissions, RbacService rbac, EntityManager entityManager,
            MessageSource messages, PlatformTransactionManager transactionManager) {
        this.portal = portal;
        this.reviews = reviews;
        this.skills = skills;
        this.versions = versions;
        this.audits = audits;
        this.namespaces = namespaces;
        this.members = members;
        this.permissions = permissions;
        this.rbac = rbac;
        this.entityManager = entityManager;
        this.messages = messages;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setTimeout(10);
    }

    public BatchReviewResponse process(BatchReviewRequest request, String actor,
            AuditRequestContext context, boolean preview) {
        if (request.ids() == null || request.ids().isEmpty() || request.ids().size() > 50
                || request.action() == null || request.ids().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(request.ids()).size() != request.ids().size()
                || request.comment() != null && request.comment().length() > 2000) {
            throw new DomainBadRequestException("review.batch.invalid");
        }
        if (!preview && request.action() == BatchReviewRequest.Action.REJECT
                && (request.comment() == null || request.comment().isBlank())) {
            throw new DomainBadRequestException("review.batch.reasonRequired");
        }
        var results = new ArrayList<BatchReviewResponse.BatchReviewItem>();
        long started = System.nanoTime();
        for (Long id : request.ids()) {
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.SECONDS.toNanos(25)) {
                results.add(item(id, "SKIPPED", "review.batch.timeBudget"));
                continue;
            }
            try {
                // Catch outside execute: commit/flush failures belong to this item, never the next one.
                results.add(transactions.execute(status -> decide(id, request, actor, context, preview)));
            } catch (LocalizedDomainException exception) {
                results.add(item(id, "SKIPPED", exception.messageCode(), exception.messageArgs()));
            } catch (ConcurrentModificationException | ConcurrencyFailureException exception) {
                results.add(item(id, "SKIPPED", "review.batch.conflict"));
            } catch (RuntimeException exception) {
                log.error("Batch review item {} failed for actor {}", id, actor, exception);
                results.add(item(id, "UNKNOWN", "review.batch.unknown"));
            }
        }
        return new BatchReviewResponse(results);
    }

    private BatchReviewResponse.BatchReviewItem decide(Long id, BatchReviewRequest request, String actor,
            AuditRequestContext context, boolean preview) {
        entityManager.clear();
        ReviewTask task = reviews.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("review_task.not_found", id));
        authorizedRoles(task, actor);
        // Never dispatch another review kind via this new entry point.
        if (task.getSubjectType() != ReviewSubjectType.SKILL_VERSION
                || task.getRequestedVisibility() != null || task.getAvatarCandidateId() != null
                || task.isAvatarRemoval()) {
            throw new DomainBadRequestException("review.batch.unsupported");
        }
        if (!preview) {
            var skill = skills.findLockedById(task.getSkillId())
                    .orElseThrow(() -> new DomainNotFoundException("skill.not_found", task.getSkillId()));
            entityManager.refresh(skill);
            var version = versions.findByIdForUpdate(task.getSkillVersionId())
                    .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
            entityManager.refresh(version);
            entityManager.refresh(task);
        }
        // Refresh memberships for EVERY item, after waiting for resource locks; request attributes
        // can be stale during a long batch. The existing portal also reloads platform roles.
        var roles = authorizedRoles(task, actor);
        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("review.not_pending", id);
        }
        var version = versions.findById(task.getSkillVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", task.getSkillVersionId()));
        if (request.action() == BatchReviewRequest.Action.APPROVE) {
            boolean newerPublished = skills.findById(version.getSkillId())
                    .map(skill -> skill.getLatestVersionId() != null && skill.getLatestVersionId() > version.getId()
                            && versions.findById(skill.getLatestVersionId())
                                .map(latest -> latest.getStatus() == SkillVersionStatus.PUBLISHED).orElse(false))
                    .orElse(false);
            if (newerPublished) throw new DomainBadRequestException("review.batch.newerPublished");
            boolean safe = audits.findLatestActiveByVersionIdAndScannerType(version.getId(), ScannerType.SKILL_SCANNER)
                    .filter(audit -> audit.getScannedAt() != null && audit.getFailureReason() == null)
                    .filter(audit -> audit.getVerdict() == SecurityVerdict.SAFE)
                    .filter(audit -> Boolean.TRUE.equals(audit.getIsSafe())).isPresent();
            if (version.getStatus() != SkillVersionStatus.PENDING_REVIEW || !safe) {
                throw new DomainBadRequestException("review.batch.scanRequired");
            }
        }
        if (preview) return item(id, "READY", "review.batch.ready");
        if (request.action() == BatchReviewRequest.Action.APPROVE) {
            portal.approveReview(id, request.comment(), actor, roles, context);
            return item(id, "APPROVED", "review.batch.approved");
        }
        portal.rejectReview(id, request.comment().trim(), actor, roles, context);
        return item(id, "REJECTED", "review.batch.rejected");
    }

    private Map<Long, NamespaceRole> authorizedRoles(ReviewTask task, String actor) {
        var roles = members.findByNamespaceIdAndUserId(task.getNamespaceId(), actor)
                .map(member -> {
                    entityManager.refresh(member);
                    return Map.of(task.getNamespaceId(), member.getRole());
                }).orElseGet(Map::of);
        var namespace = namespaces.findById(task.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", task.getNamespaceId()));
        entityManager.refresh(namespace);
        if (!permissions.canReview(task, actor, namespace.getType(), roles, rbac.getUserRoleCodes(actor))) {
            throw new DomainForbiddenException("review.no_permission");
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("review.batch.namespaceInactive");
        }
        return roles;
    }

    private BatchReviewResponse.BatchReviewItem item(Long id, String outcome, String reason, Object... args) {
        return new BatchReviewResponse.BatchReviewItem(id, outcome, reason,
                messages.getMessage(reason, args, reason, LocaleContextHolder.getLocale()));
    }
}
