package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Converges bound member versions without changing their identity. */
@Service
public class SkillSuiteBundleMemberProgressService {

    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final SkillSuiteBundleActorContextService actorContextService;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillReviewSubmitService skillReviewSubmitService;
    private final Clock clock;

    public SkillSuiteBundleMemberProgressService(
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleMemberResultRepository memberRepository,
            SkillSuiteBundleActorContextService actorContextService,
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            SkillReviewSubmitService skillReviewSubmitService,
            Clock clock
    ) {
        this.operationRepository = operationRepository;
        this.memberRepository = memberRepository;
        this.actorContextService = actorContextService;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillReviewSubmitService = skillReviewSubmitService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProgressOutcome reconcile(String operationId) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElse(null);
        if (operation == null || terminal(operation.getStatus())) {
            return ProgressOutcome.TERMINAL;
        }
        SkillSuiteBundleActorContextService.ActorContext actor =
                actorContextService.requireCurrent(operation.getActorId());
        List<SkillSuiteBundleMemberResult> members =
                memberRepository.findByOperationIdOrderByPositionForUpdate(operationId);
        Instant now = clock.instant();
        boolean waiting = false;
        for (SkillSuiteBundleMemberResult member : members) {
            if (member.getStatus() == SkillSuiteBundleMemberResultStatus.PLANNED
                    || member.getStatus() == SkillSuiteBundleMemberResultStatus.RUNNING) {
                operation.markRunning(now);
                operationRepository.save(operation);
                return ProgressOutcome.HAS_PLANNED;
            }
            if (member.getStatus() == SkillSuiteBundleMemberResultStatus.BLOCKED_RETRYABLE) {
                operation.markBlockedRetryable("MEMBER_RETRY_REQUIRED", null, now);
                operationRepository.save(operation);
                return ProgressOutcome.TERMINAL;
            }
            if (member.getStatus() != SkillSuiteBundleMemberResultStatus.WAITING_FOR_MEMBER) {
                continue;
            }
            MemberState state = loadState(member, operation, actor);
            switch (state.version().getStatus()) {
                case PUBLISHED -> {
                    if (!state.version().isDownloadReady() || state.version().getYankedAt() != null) {
                        throw stateChanged();
                    }
                    member.markCompleted(now);
                }
                case SCANNING, PENDING_REVIEW -> waiting = true;
                case UPLOADED -> {
                    if (state.skill().getVisibility() != SkillVisibility.PRIVATE) {
                        throw stateChanged();
                    }
                    skillReviewSubmitService.confirmPublish(
                            state.skill().getId(), state.version().getId(), operation.getActorId(),
                            actor.namespaceRoles(), actor.platformRoles());
                    member.markCompleted(now);
                }
                case SCAN_FAILED -> {
                    member.markBlockedRetryable("MEMBER_SCAN_FAILED", now);
                    operation.markBlockedRetryable("MEMBER_SCAN_FAILED", null, now);
                    memberRepository.saveAll(members);
                    operationRepository.save(operation);
                    return ProgressOutcome.TERMINAL;
                }
                case REJECTED, DRAFT, YANKED -> throw stateChanged();
            }
        }
        memberRepository.saveAll(members);
        if (waiting) {
            operation.markWaitingForMembers(now);
            operationRepository.save(operation);
            return ProgressOutcome.WAITING;
        }
        boolean allCompleted = members.stream().allMatch(
                member -> member.getStatus() == SkillSuiteBundleMemberResultStatus.COMPLETED);
        if (!allCompleted) {
            throw stateChanged();
        }
        operation.markRunning(now);
        operationRepository.save(operation);
        return ProgressOutcome.READY_FOR_DRAFT;
    }

    private MemberState loadState(
            SkillSuiteBundleMemberResult member,
            SkillSuiteBundleExecutionOperation operation,
            SkillSuiteBundleActorContextService.ActorContext actor
    ) {
        Skill skill = skillRepository.findById(Objects.requireNonNull(member.getSkillId()))
                .orElseThrow(this::stateChanged);
        SkillVersion version = skillVersionRepository.findById(Objects.requireNonNull(member.getSkillVersionId()))
                .orElseThrow(this::stateChanged);
        Namespace namespace = namespaceRepository.findBySlug(member.getNamespaceSlug())
                .orElseThrow(this::stateChanged);
        if (!skill.getNamespaceId().equals(namespace.getId())
                || namespace.getStatus() != NamespaceStatus.ACTIVE
                || !skill.getSlug().equals(member.getSkillSlug())
                || skill.getStatus() != SkillStatus.ACTIVE
                || skill.isHidden()
                || !version.getSkillId().equals(skill.getId())
                || !version.getVersion().equals(member.getRequestedVersion())
                || skill.getVisibility() != member.getRequestedVisibility()
                || !canManage(skill, operation.getActorId(), actor.namespaceRoles(), actor.platformRoles())) {
            throw stateChanged();
        }
        return new MemberState(skill, version);
    }

    private boolean canManage(
            Skill skill,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        NamespaceRole role = namespaceRoles.get(skill.getNamespaceId());
        return skill.getOwnerId().equals(actorId)
                || role == NamespaceRole.OWNER
                || role == NamespaceRole.ADMIN
                || platformRoles.contains("SUPER_ADMIN");
    }

    private boolean terminal(SkillSuiteBundleOperationStatus status) {
        return status == SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED
                || status == SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED
                || status == SkillSuiteBundleOperationStatus.CANCELLED;
    }

    private DomainBadRequestException stateChanged() {
        return new DomainBadRequestException("error.suite.bundle.member.stateChanged");
    }

    private record MemberState(Skill skill, SkillVersion version) {
    }

    public enum ProgressOutcome {
        HAS_PLANNED,
        WAITING,
        READY_FOR_DRAFT,
        TERMINAL
    }
}
