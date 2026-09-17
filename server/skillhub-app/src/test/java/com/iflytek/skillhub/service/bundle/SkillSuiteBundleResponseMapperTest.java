package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleCoordinate;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberSourceType;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMode;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSuiteBundleResponseMapperTest {

    @Test
    void exposesPublicationDiffButNotTemporaryObjectKeys() {
        SkillSuiteBundlePreviewPlanner.MemberPlan member = new SkillSuiteBundlePreviewPlanner.MemberPlan(
                new SkillSuiteBundleCoordinate("global", "member"), SkillSuiteBundleMemberSourceType.PACKAGE,
                SkillSuiteBundleRelationshipChange.ADDED, SkillSuiteBundlePublishAction.CREATE_SKILL,
                null, null, SkillVisibility.PUBLIC, "1.0.0", "sha256:member",
                List.of(new SkillSuiteBundlePackageAnalyzer.StagedMemberFile(
                        "SKILL.md", 100, "text/markdown", "a".repeat(64),
                        "temporary/suite-bundles/private/entries/1")),
                List.of(), List.of("credential warning"));
        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = new SkillSuiteBundlePreviewPlanner.PreviewPlan(
                SkillSuiteBundleMode.CREATE, new SkillSuiteBundleCoordinate("global", "suite"),
                1L, null, null, "1.0.0", "Suite", "Summary", "Overview",
                SkillVisibility.PUBLIC, List.of(member), List.of(), List.of(),
                List.of("@global/member: credential warning"), "warning-digest");
        Instant expiresAt = Instant.parse("2026-09-11T09:00:00Z");

        var response = new SkillSuiteBundleResponseMapper().toResponse(
                new SkillSuiteBundlePreviewAppService.PreviewOutcome(
                        "preview-1", expiresAt, null, plan));

        assertThat(response.previewToken()).isEqualTo("preview-1");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.target().coordinate()).isEqualTo("@global/suite");
        assertThat(response.members()).singleElement().satisfies(mapped -> {
            assertThat(mapped.coordinate()).isEqualTo("@global/member");
            assertThat(mapped.publishAction()).isEqualTo(SkillSuiteBundlePublishAction.CREATE_SKILL);
            assertThat(mapped.warnings()).containsExactly("credential warning");
        });
        assertThat(response.toString()).doesNotContain("temporary/suite-bundles");
    }
}
