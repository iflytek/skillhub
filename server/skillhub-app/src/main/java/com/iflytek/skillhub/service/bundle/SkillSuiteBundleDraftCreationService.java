package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.suite.CreateSkillSuiteDraftCommand;
import com.iflytek.skillhub.domain.suite.SkillSuiteActionContext;
import com.iflytek.skillhub.domain.suite.SkillSuiteDraftService;
import com.iflytek.skillhub.domain.suite.SkillSuiteMemberSelection;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Atomically creates the Suite draft only after every bound member is published. */
@Service
public class SkillSuiteBundleDraftCreationService {

    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final SkillSuiteBundlePreviewSessionRepository previewRepository;
    private final SkillSuiteBundleActorContextService actorContextService;
    private final SkillSuiteDraftService draftService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SkillSuiteBundleDraftCreationService(
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleMemberResultRepository memberRepository,
            SkillSuiteBundlePreviewSessionRepository previewRepository,
            SkillSuiteBundleActorContextService actorContextService,
            SkillSuiteDraftService draftService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.operationRepository = operationRepository;
        this.memberRepository = memberRepository;
        this.previewRepository = previewRepository;
        this.actorContextService = actorContextService;
        this.draftService = draftService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean create(String operationId) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElse(null);
        if (operation == null || operation.getStatus() == SkillSuiteBundleOperationStatus.CANCELLED
                || operation.getStatus() == SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED) {
            return false;
        }
        if (operation.getStatus() == SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED) {
            return true;
        }
        List<SkillSuiteBundleMemberResult> members =
                memberRepository.findByOperationIdOrderByPositionForUpdate(operationId);
        if (members.isEmpty() || members.stream().anyMatch(
                member -> member.getStatus() != SkillSuiteBundleMemberResultStatus.COMPLETED)) {
            return false;
        }
        SkillSuiteBundlePreviewSession preview = previewRepository.findById(operation.getPreviewToken())
                .orElseThrow(this::stateChanged);
        SkillSuiteBundleManifest manifest = objectMapper.convertValue(
                preview.getManifest(), SkillSuiteBundleManifest.class);
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = objectMapper.convertValue(
                operation.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class);
        if (manifest.spec().mode() != operation.getMode()
                || plan.mode() != operation.getMode()
                || !manifest.metadata().coordinate().equals(plan.target())
                || !manifest.metadata().coordinate().slug().equals(operation.getTargetSuiteSlug())
                || !manifest.spec().version().equals(operation.getTargetVersion())
                || !Objects.equals(plan.targetNamespaceId(), operation.getNamespaceId())
                || !Objects.equals(plan.targetSuiteId(), operation.getTargetSuiteId())
                || !Objects.equals(plan.baseSuiteVersionId(), operation.getBaseSuiteVersionId())
                || !Objects.equals(plan.targetVersion(), operation.getTargetVersion())) {
            throw stateChanged();
        }
        SkillSuiteBundleActorContextService.ActorContext actor =
                actorContextService.requireCurrent(operation.getActorId());
        Long entryVersionId = members.stream()
                .filter(member -> member.getNamespaceSlug().equals(manifest.spec().entry().namespace()))
                .filter(member -> member.getSkillSlug().equals(manifest.spec().entry().slug()))
                .map(SkillSuiteBundleMemberResult::getSkillVersionId)
                .findFirst()
                .orElseThrow(this::stateChanged);
        List<SkillSuiteMemberSelection> selections = members.stream()
                .map(member -> new SkillSuiteMemberSelection(
                        member.getSkillId(), member.getSkillVersionId(), member.getNamespaceSlug(),
                        member.getSkillSlug(), member.getRequestedVersion(), member.getFingerprint()))
                .toList();
        CreateSkillSuiteDraftCommand command = new CreateSkillSuiteDraftCommand(
                operation.getNamespaceId(), operation.getTargetSuiteSlug(), plan.displayName(),
                plan.summary(), plan.overview(), operation.getTargetVersion(), plan.visibility(),
                manifest.spec().changelog(), entryVersionId, selections);
        SkillSuiteActionContext context = new SkillSuiteActionContext(
                operation.getActorId(), actor.namespaceRoles(), actor.platformRoles(),
                operationId, null, null);
        SkillSuiteDraftService.CreatedDraft created = operation.getMode() == SkillSuiteBundleMode.CREATE
                ? draftService.create(command, context)
                : draftService.createVersion(operation.getTargetSuiteId(), command, context);
        operation.markSuiteDraftCreated(
                created.suite().getId(), created.version().getId(), clock.instant());
        operationRepository.save(operation);
        operationRepository.flush();
        return true;
    }

    private DomainBadRequestException stateChanged() {
        return new DomainBadRequestException("error.suite.bundle.member.stateChanged");
    }
}
