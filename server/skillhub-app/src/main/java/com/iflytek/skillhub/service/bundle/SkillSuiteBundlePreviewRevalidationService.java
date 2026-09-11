package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifest;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMember;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Rebuilds a PreviewSession against current authorization and lifecycle state without file extraction. */
@Service
public class SkillSuiteBundlePreviewRevalidationService {

    private final SkillSuiteBundlePreviewPlanner planner;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;

    public SkillSuiteBundlePreviewRevalidationService(
            SkillSuiteBundlePreviewPlanner planner,
            ObjectStorageService objectStorageService,
            ObjectMapper objectMapper
    ) {
        this.planner = planner;
        this.objectStorageService = objectStorageService;
        this.objectMapper = objectMapper;
    }

    public ValidatedPreview requireUnchanged(
            SkillSuiteBundlePreviewSession preview,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        if (!objectStorageService.exists(preview.getArchiveObjectKey())) {
            throw stateChanged();
        }
        SkillSuiteBundlePreviewPlanner.PreviewPlan previewPlan = objectMapper.convertValue(
                preview.getPlan(), SkillSuiteBundlePreviewPlanner.PreviewPlan.class);
        SkillSuiteBundleManifest manifest = objectMapper.convertValue(
                preview.getManifest(), SkillSuiteBundleManifest.class);
        SkillSuiteBundlePreviewPlanner.PreviewPlan currentPlan = planner.plan(
                rebuildAnalysis(manifest, previewPlan), actorId, namespaceRoles, platformRoles);
        if (!currentPlan.confirmable() || !currentPlan.equals(previewPlan)) {
            throw stateChanged();
        }
        return new ValidatedPreview(manifest, previewPlan);
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

    private DomainBadRequestException stateChanged() {
        return new DomainBadRequestException("error.suite.bundle.preview.stateChanged");
    }

    public record ValidatedPreview(
            SkillSuiteBundleManifest manifest,
            SkillSuiteBundlePreviewPlanner.PreviewPlan plan
    ) {
    }
}
