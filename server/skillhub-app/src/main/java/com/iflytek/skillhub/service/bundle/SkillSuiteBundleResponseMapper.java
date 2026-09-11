package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationResponse;
import com.iflytek.skillhub.dto.SkillSuiteBundlePreviewResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Maps internal Bundle plans to stable transport responses without exposing staged object keys. */
@Component
public class SkillSuiteBundleResponseMapper {

    public SkillSuiteBundlePreviewResponse toResponse(
            SkillSuiteBundlePreviewAppService.PreviewOutcome outcome
    ) {
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = outcome.plan();
        if (plan == null) {
            var manifest = outcome.packageAnalysis().manifest();
            List<SkillSuiteBundlePreviewResponse.Member> members = outcome.packageAnalysis()
                    .packageMembers().stream()
                    .map(member -> new SkillSuiteBundlePreviewResponse.Member(
                            member.coordinate().canonical(), SkillSuiteBundleMemberSourceType.PACKAGE,
                            SkillSuiteBundleRelationshipChange.ADDED, null, null, null, null,
                            member.metadata() == null ? null : member.metadata().version(),
                            member.fingerprint(), member.validation().errors(), member.validation().warnings()))
                    .toList();
            List<String> warnings = new ArrayList<>();
            outcome.packageAnalysis().packageMembers().forEach(member -> member.validation().warnings()
                    .forEach(warning -> warnings.add(member.coordinate().canonical() + ": " + warning)));
            return new SkillSuiteBundlePreviewResponse(
                    null, null, false,
                    new SkillSuiteBundlePreviewResponse.Target(
                            manifest.spec().mode(), manifest.metadata().coordinate().canonical(), null, null, null,
                            manifest.spec().version(), manifest.spec().displayName(), manifest.spec().summary(),
                            manifest.spec().overview(), manifest.spec().visibility()),
                    members, List.of(), outcome.errors(), List.copyOf(warnings), null);
        }

        List<SkillSuiteBundlePreviewResponse.Member> members = plan.members().stream()
                .map(member -> new SkillSuiteBundlePreviewResponse.Member(
                        member.coordinate().canonical(), member.sourceType(), member.relationship(),
                        member.publishAction(), member.skillId(), member.skillVersionId(),
                        member.finalVisibility(), member.resolvedVersion(), member.fingerprint(),
                        member.errors(), member.warnings()))
                .toList();
        List<SkillSuiteBundlePreviewResponse.RemovedMember> removed = plan.removedMembers().stream()
                .map(member -> new SkillSuiteBundlePreviewResponse.RemovedMember(
                        member.coordinate().canonical(), member.skillId(), member.skillVersionId(),
                        member.version(), member.entry()))
                .toList();
        return new SkillSuiteBundlePreviewResponse(
                outcome.previewToken(), outcome.expiresAt(), outcome.confirmable(),
                new SkillSuiteBundlePreviewResponse.Target(
                        plan.mode(), plan.target().canonical(), plan.targetNamespaceId(), plan.targetSuiteId(),
                        plan.baseSuiteVersionId(), plan.targetVersion(), plan.displayName(), plan.summary(),
                        plan.overview(), plan.visibility()),
                members, removed, plan.errors(), plan.warnings(), plan.warningDigest());
    }

    public SkillSuiteBundleOperationResponse toResponse(
            SkillSuiteBundleConfirmationAppService.ConfirmationOutcome outcome
    ) {
        return new SkillSuiteBundleOperationResponse(
                outcome.operationId(), outcome.status(), outcome.replayed());
    }
}
