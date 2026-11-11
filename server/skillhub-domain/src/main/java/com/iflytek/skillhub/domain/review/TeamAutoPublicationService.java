package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAudit;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides system approval of team-only automatic publication after a scan round completes.
 *
 * <p>The scan service calls {@link #lockForScanCompletion} before it mutates a version, then
 * {@link #onCurrentScanCompleted} or {@link #onCurrentScanFailed} only when that callback changed
 * the current round's version state. Locks are taken in namespace, Skill, version order, matching
 * grant revocation (namespace) and human review/withdrawal/upload (Skill). Every failed precondition
 * hands the task to normal review instead of throwing, so the scan transaction always commits.
 */
@Service
public class TeamAutoPublicationService {

    private static final Logger log = LoggerFactory.getLogger(TeamAutoPublicationService.class);

    private final ReviewTaskRepository reviewTaskRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final UserAccountRepository userAccountRepository;
    private final TeamAutoPublicationPolicy policy;
    private final ReviewAutoPublicationHandoff handoff;
    private final ReviewService reviewService;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    public TeamAutoPublicationService(ReviewTaskRepository reviewTaskRepository,
                                      NamespaceRepository namespaceRepository,
                                      SkillRepository skillRepository,
                                      SkillVersionRepository skillVersionRepository,
                                      SecurityAuditRepository securityAuditRepository,
                                      UserAccountRepository userAccountRepository,
                                      TeamAutoPublicationPolicy policy,
                                      ReviewAutoPublicationHandoff handoff,
                                      ReviewService reviewService,
                                      AuditLogService auditLogService,
                                      EntityManager entityManager) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.userAccountRepository = userAccountRepository;
        this.policy = policy;
        this.handoff = handoff;
        this.reviewService = reviewService;
        this.auditLogService = auditLogService;
        this.entityManager = entityManager;
    }

    /**
     * Serializes scan completion with grant changes and lifecycle operations for eligible work.
     * The grant snapshot is immutable once written, so an unlocked read decides whether to lock.
     */
    public void lockForScanCompletion(Long versionId, SecurityAudit audit) {
        Optional<ReviewTask> candidate = eligiblePendingTask(versionId);
        if (candidate.isEmpty()) {
            return;
        }
        ReviewTask task = candidate.get();
        namespaceRepository.findLockedById(task.getNamespaceId()).ifPresent(entityManager::refresh);
        skillRepository.findLockedById(task.getSkillId()).ifPresent(entityManager::refresh);
        skillVersionRepository.findByIdForUpdate(versionId).ifPresent(entityManager::refresh);
        // A competing callback may have settled this round while this transaction waited.
        entityManager.refresh(audit);
        // A competing withdrawal may have deleted the task while this transaction waited; re-read it.
        entityManager.detach(task);
    }

    /** Called once by the callback that completed the version's current scan round. */
    public void onCurrentScanCompleted(Long versionId, String scanTaskId, SecurityVerdict verdict) {
        // Review status updates clear the persistence context; persist the audit/version first.
        entityManager.flush();
        Optional<ReviewTask> candidate = eligiblePendingTask(versionId);
        if (candidate.isEmpty()) {
            return;
        }
        ReviewTask task = candidate.get();
        Optional<String> rejection = verdict == SecurityVerdict.SAFE
                ? approvalRejection(task, versionId, scanTaskId)
                : Optional.of("SCAN_NOT_SAFE");
        if (rejection.isPresent()) {
            fallBack(task, rejection.get(), scanTaskId);
            return;
        }
        SkillVersion version = skillVersionRepository.findById(versionId).orElseThrow();
        Skill skill = skillRepository.findById(task.getSkillId()).orElseThrow();
        UUID grantId = task.getAutoPublicationGrantId();
        if (!reviewService.approveAutomatically(task, skill, version, scanTaskId)) {
            return;
        }
        auditLogService.record(null, "TEAM_AUTO_PUBLICATION_APPROVE", "REVIEW_TASK", task.getId(),
                null, null, null, AuditDetail.builder()
                        .put("namespaceId", task.getNamespaceId())
                        .put("submittedBy", task.getSubmittedBy())
                        .put("skillId", task.getSkillId())
                        .put("skillVersionId", versionId)
                        .put("scanTaskId", scanTaskId)
                        .put("grantId", grantId.toString())
                        .put("decisionMethod", ReviewDecisionMethod.SYSTEM_AUTOMATIC.name())
                        .build());
        log.info("Team-only automatic publication approved [reviewTaskId={}, versionId={}, scanTaskId={}]",
                task.getId(), versionId, scanTaskId);
    }

    /** A failed, timed-out or unavailable current scan never approves; normal handling continues. */
    public void onCurrentScanFailed(Long versionId, String scanTaskId) {
        entityManager.flush();
        eligiblePendingTask(versionId).ifPresent(task -> fallBack(task, "SCAN_FAILED", scanTaskId));
    }

    private Optional<ReviewTask> eligiblePendingTask(Long versionId) {
        return reviewTaskRepository.findBySkillVersionIdAndStatus(versionId, ReviewTaskStatus.PENDING)
                .filter(task -> task.getAutoPublicationGrantId() != null);
    }

    private Optional<String> approvalRejection(ReviewTask task, Long versionId, String scanTaskId) {
        if (!task.hasActiveAutoPublicationEligibility()) {
            return Optional.of("ELIGIBILITY_CANCELLED");
        }
        if (task.getSubjectType() != ReviewSubjectType.SKILL_VERSION
                || task.getOriginType() != ReviewOriginType.USER
                || task.getGithubSyncIngestionId() != null
                || task.getAvatarCandidateId() != null
                || task.isAvatarRemoval()
                || task.getRequestedVisibility() != null) {
            return Optional.of("OUT_OF_SCOPE");
        }
        Namespace namespace = namespaceRepository.findById(task.getNamespaceId()).orElse(null);
        if (!policy.isActiveTeam(namespace)) {
            return Optional.of("NAMESPACE_NOT_ACTIVE_TEAM");
        }
        Optional<UUID> currentGrant = policy.activeGrant(task.getNamespaceId(), task.getSubmittedBy());
        if (currentGrant.isEmpty() || !currentGrant.get().equals(task.getAutoPublicationGrantId())) {
            return Optional.of("GRANT_NOT_CURRENT");
        }
        if (userAccountRepository.findById(task.getSubmittedBy()).filter(UserAccount::isActive).isEmpty()) {
            return Optional.of("ACCOUNT_INACTIVE");
        }
        Skill skill = skillRepository.findById(task.getSkillId()).orElse(null);
        if (skill == null
                || skill.getStatus() != SkillStatus.ACTIVE
                || skill.isHidden()
                || skill.isGithubSyncManaged()
                || skill.getVisibility() != SkillVisibility.NAMESPACE_ONLY
                || !task.getSubmittedBy().equals(skill.getOwnerId())) {
            return Optional.of("SKILL_NOT_ELIGIBLE");
        }
        if (reviewTaskRepository.existsBySkillIdAndRequestedVisibilityIsNotNullAndStatus(
                skill.getId(), ReviewTaskStatus.PENDING)) {
            return Optional.of("VISIBILITY_REQUEST_PENDING");
        }
        SkillVersion version = skillVersionRepository.findById(versionId).orElse(null);
        if (version == null
                || !Objects.equals(version.getSkillId(), skill.getId())
                || version.getStatus() != SkillVersionStatus.PENDING_REVIEW
                || version.getRequestedVisibility() != SkillVisibility.NAMESPACE_ONLY
                || version.getGithubSyncIngestionId() != null) {
            return Optional.of("VERSION_NOT_ELIGIBLE");
        }
        if (isSuperseded(task, skill, version)) {
            return Optional.of("SUPERSEDED");
        }
        if (!isCurrentSafeRound(versionId, scanTaskId)) {
            return Optional.of("SCAN_NOT_CURRENT_SAFE");
        }
        if (otherOwnerPublishedSameSlug(skill)) {
            return Optional.of("NAME_CONFLICT");
        }
        return Optional.empty();
    }

    /**
     * A newer submission or publication of the same Skill supersedes this task. Publishing it would
     * move the latest pointer back to older content.
     */
    private boolean isSuperseded(ReviewTask task, Skill skill, SkillVersion version) {
        boolean newerPendingTask = reviewTaskRepository.findBySkillIdAndStatus(skill.getId(), ReviewTaskStatus.PENDING)
                .stream()
                .anyMatch(other -> !other.getId().equals(task.getId()) && other.getId() > task.getId());
        if (newerPendingTask) {
            return true;
        }
        if (skill.getLatestVersionId() == null || skill.getLatestVersionId().equals(version.getId())) {
            return false;
        }
        return skillVersionRepository.findById(skill.getLatestVersionId())
                .filter(latest -> latest.getId() > version.getId())
                .isPresent();
    }

    private boolean isCurrentSafeRound(Long versionId, String scanTaskId) {
        return securityAuditRepository.findLatestActiveByVersionIdAndScannerType(versionId, ScannerType.SKILL_SCANNER)
                .filter(audit -> scanTaskId != null && scanTaskId.equals(audit.getTaskId()))
                .filter(audit -> audit.getScannedAt() != null)
                .filter(audit -> audit.getFailureReason() == null)
                .filter(audit -> audit.getVerdict() == SecurityVerdict.SAFE)
                .map(SecurityAudit::getIsSafe)
                .orElse(false);
    }

    private boolean otherOwnerPublishedSameSlug(Skill skill) {
        List<Skill> sameSlug = skillRepository.findByNamespaceIdAndSlug(skill.getNamespaceId(), skill.getSlug());
        return sameSlug.stream()
                .filter(other -> !other.getId().equals(skill.getId()))
                .anyMatch(other -> !skillVersionRepository
                        .findBySkillIdAndStatus(other.getId(), SkillVersionStatus.PUBLISHED)
                        .isEmpty());
    }

    private void fallBack(ReviewTask task, String reason, String scanTaskId) {
        boolean firstHandoff = !task.isAutoPublicationCancelled() || !task.isManualReviewNotified();
        handoff.handOff(task);
        if (!firstHandoff) {
            return;
        }
        auditLogService.record(null, "TEAM_AUTO_PUBLICATION_FALLBACK", "REVIEW_TASK", task.getId(),
                null, null, null, AuditDetail.builder()
                        .put("namespaceId", task.getNamespaceId())
                        .put("submittedBy", task.getSubmittedBy())
                        .put("skillVersionId", task.getSkillVersionId())
                        .put("scanTaskId", scanTaskId)
                        .put("grantId", task.getAutoPublicationGrantId().toString())
                        .put("reason", reason)
                        .build());
        log.info("Team-only automatic publication fell back to normal review [reviewTaskId={}, reason={}]",
                task.getId(), reason);
    }
}
