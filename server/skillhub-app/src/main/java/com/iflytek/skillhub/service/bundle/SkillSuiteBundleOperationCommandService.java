package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.event.SkillSuiteBundleAdvanceRequestedEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationAuthorizationPolicy;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SecurityScanRetryAppService;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies non-destructive control commands to a locked Bundle operation. */
@Service
public class SkillSuiteBundleOperationCommandService {

    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final SkillSuiteBundlePreviewSessionRepository previewRepository;
    private final SkillSuiteBundlePreviewRevalidationService revalidationService;
    private final SecurityScanRetryAppService securityScanRetryAppService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SkillSuiteBundleOperationCommandService(
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleMemberResultRepository memberRepository,
            SkillSuiteBundlePreviewSessionRepository previewRepository,
            SkillSuiteBundlePreviewRevalidationService revalidationService,
            SecurityScanRetryAppService securityScanRetryAppService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.operationRepository = operationRepository;
        this.memberRepository = memberRepository;
        this.previewRepository = previewRepository;
        this.revalidationService = revalidationService;
        this.securityScanRetryAppService = securityScanRetryAppService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SkillSuiteBundleOperationResponse cancel(
            String operationId,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(this::notFound);
        if (!SkillSuiteBundleOperationAuthorizationPolicy.canAccess(
                operation, actorId, namespaceRoles, platformRoles)) {
            throw notFound();
        }
        Instant now = clock.instant();
        boolean changed = operation.cancel(now);
        if (changed) {
            List<SkillSuiteBundleMemberResult> members =
                    memberRepository.findByOperationIdOrderByPositionForUpdate(operationId);
            members.forEach(member -> member.cancelUnlessCompleted(now));
            memberRepository.saveAll(members);
            operationRepository.save(operation);
            operationRepository.flush();
        }
        return new SkillSuiteBundleOperationResponse(
                operation.getOperationId(), operation.getStatus().name(), !changed);
    }

    @Transactional
    public SkillSuiteBundleOperationResponse retry(
            String operationId,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(this::notFound);
        if (!SkillSuiteBundleOperationAuthorizationPolicy.canAccess(
                operation, actorId, namespaceRoles, platformRoles)) {
            throw notFound();
        }
        operation.requireRetryable();
        SkillSuiteBundlePreviewSession preview = previewRepository.findById(operation.getPreviewToken())
                .orElse(null);
        Instant now = clock.instant();
        List<SkillSuiteBundleMemberResult> members =
                memberRepository.findByOperationIdOrderByPositionForUpdate(operationId);
        if ("MEMBER_SCAN_FAILED".equals(operation.getFailureCode())) {
            retryFailedScan(operation, members, actorId, namespaceRoles, platformRoles, now);
        } else if (preview == null || (!hasProgress(members)
                && !planRemainsValid(preview, actorId, namespaceRoles, platformRoles))) {
            operation.markRepreviewRequired("BUNDLE_PLAN_CHANGED", now);
            members.forEach(member -> member.requireRepreviewUnlessCompleted(now));
        } else {
            operation.retry(now);
            members.forEach(member -> member.retryUnlessCompleted(now));
        }
        memberRepository.saveAll(members);
        operationRepository.save(operation);
        operationRepository.flush();
        if (operation.getStatus() == com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus.RUNNING) {
            eventPublisher.publishEvent(new SkillSuiteBundleAdvanceRequestedEvent(operationId));
        }
        return new SkillSuiteBundleOperationResponse(
                operation.getOperationId(), operation.getStatus().name(), false);
    }

    private void retryFailedScan(
            SkillSuiteBundleExecutionOperation operation,
            List<SkillSuiteBundleMemberResult> members,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            Instant now
    ) {
        SkillSuiteBundleMemberResult failed = members.stream()
                .filter(member -> member.getStatus()
                        == com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus.BLOCKED_RETRYABLE)
                .filter(member -> member.getSkillId() != null && member.getSkillVersionId() != null)
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.suite.bundle.preview.stateChanged"));
        securityScanRetryAppService.retry(
                failed.getSkillId(), failed.getSkillVersionId(), actorId, platformRoles, namespaceRoles,
                new AuditRequestContext(null, "SkillSuiteBundle"));
        operation.retry(now);
        failed.markWaiting(now);
    }

    private boolean hasProgress(List<SkillSuiteBundleMemberResult> members) {
        return members.stream().anyMatch(member -> member.getSkillVersionId() != null);
    }

    private boolean planRemainsValid(
            SkillSuiteBundlePreviewSession preview,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        try {
            revalidationService.requireUnchanged(preview, actorId, namespaceRoles, platformRoles);
            return true;
        } catch (DomainBadRequestException exception) {
            if (!"error.suite.bundle.preview.stateChanged".equals(exception.messageCode())) {
                throw exception;
            }
            return false;
        }
    }

    private DomainNotFoundException notFound() {
        return new DomainNotFoundException("error.suite.bundle.operation.notFound");
    }
}
