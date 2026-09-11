package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillSuiteBundleProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMember;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Atomically confirms one exact PreviewSession and acquires its Suite target reservation. */
@Service
public class SkillSuiteBundleConfirmationAppService {

    private final SkillSuiteBundlePreviewSessionRepository previewRepository;
    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final SkillSuiteBundlePreviewPlanner planner;
    private final ObjectStorageService objectStorageService;
    private final SkillSuiteBundleProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SkillSuiteBundleConfirmationAppService(
            SkillSuiteBundlePreviewSessionRepository previewRepository,
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleMemberResultRepository memberRepository,
            SkillSuiteBundlePreviewPlanner planner,
            ObjectStorageService objectStorageService,
            SkillSuiteBundleProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.previewRepository = previewRepository;
        this.operationRepository = operationRepository;
        this.memberRepository = memberRepository;
        this.planner = planner;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ConfirmationOutcome confirm(
            String previewToken,
            String clientRequestId,
            String confirmedWarningDigest,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        requireEnabled();
        String normalizedRequestId = normalizeRequestId(clientRequestId);
        ConfirmationOutcome existing = findIdempotent(actorId, normalizedRequestId, previewToken);
        if (existing != null) {
            return existing;
        }

        SkillSuiteBundlePreviewSession preview = previewRepository.findByIdForUpdate(previewToken)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.bundle.preview.notFound"));

        // A concurrent confirmation may have committed while this transaction waited for the row lock.
        existing = findIdempotent(actorId, normalizedRequestId, previewToken);
        if (existing != null) {
            return existing;
        }

        Instant now = clock.instant();
        preview.requireConfirmableBy(actorId, confirmedWarningDigest, now);
        if (!objectStorageService.exists(preview.getArchiveObjectKey())) {
            throw new DomainBadRequestException("error.suite.bundle.preview.stateChanged");
        }
        SkillSuiteBundlePreviewPlanner.PreviewPlan previewPlan = objectMapper.convertValue(
                preview.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class);
        SkillSuiteBundleManifest manifest = objectMapper.convertValue(
                preview.getManifest(), SkillSuiteBundleManifest.class);
        SkillSuiteBundlePreviewPlanner.PreviewPlan currentPlan = planner.plan(
                rebuildAnalysis(manifest, previewPlan), actorId, namespaceRoles, platformRoles);
        if (!currentPlan.confirmable() || !currentPlan.equals(previewPlan)) {
            throw new DomainBadRequestException("error.suite.bundle.preview.stateChanged");
        }

        String operationId = UUID.randomUUID().toString();
        SkillSuiteBundleExecutionOperation operation = new SkillSuiteBundleExecutionOperation(
                operationId, previewToken, normalizedRequestId, actorId, preview.getMode(),
                preview.getNamespaceId(), preview.getTargetSuiteSlug(), preview.getTargetSuiteId(),
                preview.getBaseSuiteVersionId(), preview.getTargetVersion(), preview.getArchiveObjectKey(),
                preview.getArchiveSha256(), preview.getPlan(), preview.getWarningDigest(), now);
        try {
            operationRepository.save(operation);
            // The partial unique index must win before any member lifecycle work can start.
            operationRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DomainConflictException("error.suite.bundle.confirmation.operationConflict");
        }
        try {
            memberRepository.saveAll(toMemberResults(operationId, manifest, previewPlan, now));
            memberRepository.flush();
            preview.markConfirmed(now);
            previewRepository.save(preview);
            previewRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DomainBadRequestException("error.suite.bundle.preview.stateChanged");
        }
        return new ConfirmationOutcome(operationId, operation.getStatus().name(), false);
    }

    private ConfirmationOutcome findIdempotent(
            String actorId, String requestId, String previewToken
    ) {
        SkillSuiteBundleExecutionOperation existing = operationRepository
                .findByActorIdAndClientRequestId(actorId, requestId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!existing.getPreviewToken().equals(previewToken)) {
            throw new DomainConflictException("error.suite.bundle.confirmation.operationConflict");
        }
        return new ConfirmationOutcome(existing.getOperationId(), existing.getStatus().name(), true);
    }

    private SkillSuiteBundlePackageAnalyzer.BundleAnalysis rebuildAnalysis(
            SkillSuiteBundleManifest manifest,
            SkillSuiteBundlePreviewPlanner.PreviewPlan plan
    ) {
        Map<SkillSuiteBundleCoordinate, SkillSuiteBundleMember> manifestMembers =
                manifest.spec().members().stream().collect(Collectors.toMap(
                        SkillSuiteBundleMember::coordinate, Function.identity()));
        List<SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis> packages = plan.members().stream()
                .filter(member -> member.sourceType() == SkillSuiteBundleMemberSourceType.PACKAGE)
                .map(member -> {
                    var source = Objects.requireNonNull(manifestMembers.get(member.coordinate()).packageSource());
                    SkillMetadata metadata = new SkillMetadata(
                            member.coordinate().slug(), "", member.resolvedVersion(), "", Map.of());
                    return new SkillSuiteBundlePackageAnalyzer.MemberPackageAnalysis(
                            member.coordinate(), source.path(), metadata,
                            ValidationResult.of(List.of(), member.warnings()), member.files(), member.fingerprint());
                })
                .toList();
        return new SkillSuiteBundlePackageAnalyzer.BundleAnalysis(manifest, packages, List.of());
    }

    private List<SkillSuiteBundleMemberResult> toMemberResults(
            String operationId,
            SkillSuiteBundleManifest manifest,
            SkillSuiteBundlePreviewPlanner.PreviewPlan plan,
            Instant now
    ) {
        Map<SkillSuiteBundleCoordinate, SkillSuiteBundleMember> manifestMembers =
                manifest.spec().members().stream().collect(Collectors.toMap(
                        SkillSuiteBundleMember::coordinate, Function.identity()));
        List<SkillSuiteBundleMemberResult> results = new ArrayList<>(plan.members().size());
        for (int position = 0; position < plan.members().size(); position++) {
            SkillSuiteBundlePreviewPlanner.MemberPlan member = plan.members().get(position);
            var declared = manifestMembers.get(member.coordinate());
            String packagePath = declared.packageSource() == null ? null : declared.packageSource().path();
            results.add(new SkillSuiteBundleMemberResult(
                    operationId, position, member.coordinate(), member.sourceType(), packagePath,
                    member.finalVisibility(), member.resolvedVersion(), member.relationship(),
                    member.publishAction(), member.fingerprint(), member.skillId(), member.skillVersionId(),
                    member.errors(), member.warnings(), now));
        }
        return results;
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (!RequestIdAccessor.isValid(requestId)) {
            throw new DomainBadRequestException("error.suite.bundle.confirmation.idempotencyKey.invalid");
        }
        return requestId;
    }

    private void requireEnabled() {
        if (!properties.isConfirmationEnabled()) {
            throw new DomainBadRequestException("error.suite.bundle.confirmation.disabled");
        }
    }

    public record ConfirmationOutcome(String operationId, String status, boolean replayed) {
    }
}
