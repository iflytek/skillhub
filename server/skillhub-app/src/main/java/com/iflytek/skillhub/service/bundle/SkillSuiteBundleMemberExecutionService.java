package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Executes at most one confirmed Bundle member in its own transaction. */
@Service
public class SkillSuiteBundleMemberExecutionService {

    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final SkillSuiteBundleActorContextService actorContextService;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final VisibilityChecker visibilityChecker;
    private final SkillPublishService skillPublishService;
    private final SkillReviewSubmitService skillReviewSubmitService;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SkillSuiteBundleMemberExecutionService(
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleMemberResultRepository memberRepository,
            SkillSuiteBundleActorContextService actorContextService,
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            SkillVersionRepository skillVersionRepository,
            VisibilityChecker visibilityChecker,
            SkillPublishService skillPublishService,
            SkillReviewSubmitService skillReviewSubmitService,
            ObjectStorageService objectStorageService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.operationRepository = operationRepository;
        this.memberRepository = memberRepository;
        this.actorContextService = actorContextService;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.visibilityChecker = visibilityChecker;
        this.skillPublishService = skillPublishService;
        this.skillReviewSubmitService = skillReviewSubmitService;
        this.objectStorageService = objectStorageService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionOutcome executeNext(String operationId) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElse(null);
        if (operation == null || operation.getStatus() != SkillSuiteBundleOperationStatus.RUNNING) {
            return ExecutionOutcome.NONE;
        }
        List<SkillSuiteBundleMemberResult> members =
                memberRepository.findByOperationIdOrderByPositionForUpdate(operationId);
        SkillSuiteBundleMemberResult member = members.stream()
                .filter(candidate -> candidate.getStatus()
                        == com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus.PLANNED)
                .findFirst()
                .orElse(null);
        if (member == null) {
            return ExecutionOutcome.NONE;
        }
        Instant now = clock.instant();
        if (!member.start(now)) {
            return ExecutionOutcome.NONE;
        }

        SkillSuiteBundleActorContextService.ActorContext actor =
                actorContextService.requireCurrent(operation.getActorId());
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = objectMapper.convertValue(
                operation.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class);
        SkillSuiteBundlePreviewPlanner.MemberPlan planned = plan.members().stream()
                .filter(candidate -> candidate.coordinate().namespace().equals(member.getNamespaceSlug()))
                .filter(candidate -> candidate.coordinate().slug().equals(member.getSkillSlug()))
                .findFirst()
                .orElseThrow(this::stateChanged);
        assertPlanBinding(member, planned);

        if (planned.publishAction() == SkillSuiteBundlePublishAction.REUSE_VERSION
                || planned.publishAction() == SkillSuiteBundlePublishAction.REFERENCE_VERSION) {
            completeExisting(operation, member, actor, now);
        } else if (planned.publishAction() == SkillSuiteBundlePublishAction.CREATE_SKILL
                || planned.publishAction() == SkillSuiteBundlePublishAction.CREATE_VERSION) {
            publishPackage(operation, member, planned, actor, now);
        } else {
            throw stateChanged();
        }
        memberRepository.saveAll(List.of(member));
        memberRepository.flush();
        return ExecutionOutcome.PROGRESSED;
    }

    private void publishPackage(
            SkillSuiteBundleExecutionOperation operation,
            SkillSuiteBundleMemberResult member,
            SkillSuiteBundlePreviewPlanner.MemberPlan planned,
            SkillSuiteBundleActorContextService.ActorContext actor,
            Instant now
    ) {
        if (planned.files().isEmpty() || member.getRequestedVisibility() == null) {
            throw stateChanged();
        }
        List<PackageEntry> entries = readEntries(planned.files());
        SkillPublishService.PublishResult result = skillPublishService.publishBundleMemberFromEntries(
                member.getNamespaceSlug(), member.getSkillId(), member.getSkillSlug(),
                member.getRequestedVersion(), entries, planned.files().stream().collect(Collectors.toUnmodifiableMap(
                        SkillSuiteBundlePackageAnalyzer.StagedMemberFile::relativePath,
                        SkillSuiteBundlePackageAnalyzer.StagedMemberFile::sha256)),
                operation.getActorId(),
                member.getRequestedVisibility(), actor.namespaceRoles(), actor.platformRoles(), true);
        member.bindVersion(result.skillId(), result.version().getId(), now);
        advanceCreatedVersion(operation, member, result.version(), actor, now);
    }

    private void advanceCreatedVersion(
            SkillSuiteBundleExecutionOperation operation,
            SkillSuiteBundleMemberResult member,
            SkillVersion version,
            SkillSuiteBundleActorContextService.ActorContext actor,
            Instant now
    ) {
        if (version.getStatus() == SkillVersionStatus.UPLOADED
                && member.getRequestedVisibility()
                == com.iflytek.skillhub.domain.skill.SkillVisibility.PRIVATE) {
            skillReviewSubmitService.confirmPublish(
                    member.getSkillId(), version.getId(), operation.getActorId(),
                    actor.namespaceRoles(), actor.platformRoles());
            member.markCompleted(now);
            return;
        }
        if (version.getStatus() == SkillVersionStatus.PUBLISHED) {
            member.markCompleted(now);
            return;
        }
        if (version.getStatus() == SkillVersionStatus.SCANNING
                || version.getStatus() == SkillVersionStatus.PENDING_REVIEW) {
            member.markWaiting(now);
            return;
        }
        throw stateChanged();
    }

    private void completeExisting(
            SkillSuiteBundleExecutionOperation operation,
            SkillSuiteBundleMemberResult member,
            SkillSuiteBundleActorContextService.ActorContext actor,
            Instant now
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
                || !version.getSkillId().equals(skill.getId())
                || !version.getVersion().equals(member.getRequestedVersion())
                || version.getStatus() != SkillVersionStatus.PUBLISHED
                || !version.isDownloadReady()
                || version.getYankedAt() != null
                || skill.getStatus() != SkillStatus.ACTIVE
                || skill.isHidden()
                || skill.getVisibility() != member.getRequestedVisibility()
                || !visibilityChecker.canAccess(
                        skill, operation.getActorId(), actor.namespaceRoles(), actor.platformRoles())) {
            throw stateChanged();
        }
        member.markCompleted(now);
    }

    private List<PackageEntry> readEntries(
            List<SkillSuiteBundlePackageAnalyzer.StagedMemberFile> files
    ) {
        List<PackageEntry> entries = new ArrayList<>(files.size());
        for (SkillSuiteBundlePackageAnalyzer.StagedMemberFile file : files) {
            if (file.size() < 0 || file.size() >= Integer.MAX_VALUE) {
                throw stateChanged();
            }
            try (InputStream input = objectStorageService.getObject(file.objectKey())) {
                byte[] content = input.readNBytes((int) file.size() + 1);
                if (content.length != file.size()) {
                    throw stateChanged();
                }
                entries.add(new PackageEntry(
                        file.relativePath(), content, file.size(), file.contentType()));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read staged Bundle member", exception);
            }
        }
        return List.copyOf(entries);
    }

    private void assertPlanBinding(
            SkillSuiteBundleMemberResult member,
            SkillSuiteBundlePreviewPlanner.MemberPlan planned
    ) {
        if (planned.publishAction() != member.getPublishAction()
                || planned.sourceType() != member.getSourceType()
                || !Objects.equals(planned.skillId(), member.getSkillId())
                || !Objects.equals(planned.skillVersionId(), member.getSkillVersionId())
                || !Objects.equals(planned.resolvedVersion(), member.getRequestedVersion())
                || !Objects.equals(planned.fingerprint(), member.getFingerprint())) {
            throw stateChanged();
        }
    }

    private DomainBadRequestException stateChanged() {
        return new DomainBadRequestException("error.suite.bundle.member.stateChanged");
    }

    public enum ExecutionOutcome {
        PROGRESSED,
        NONE
    }
}
