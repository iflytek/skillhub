package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteMemberSelection;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteStatus;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMember;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMemberRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionStatus;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleManifestParser;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePublishAction;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleRelationshipChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundlePreviewPlannerTest {

    private NamespaceRepository namespaceRepository;
    private SkillRepository skillRepository;
    private SkillVersionRepository skillVersionRepository;
    private SkillFileRepository skillFileRepository;
    private SkillSuiteRepository suiteRepository;
    private SkillSuiteVersionRepository suiteVersionRepository;
    private SkillSuiteVersionMemberRepository suiteMemberRepository;
    private SecurityScanService securityScanService;
    private SkillSuiteBundlePreviewPlanner planner;

    @BeforeEach
    void setUp() {
        namespaceRepository = mock(NamespaceRepository.class);
        skillRepository = mock(SkillRepository.class);
        skillVersionRepository = mock(SkillVersionRepository.class);
        skillFileRepository = mock(SkillFileRepository.class);
        suiteRepository = mock(SkillSuiteRepository.class);
        suiteVersionRepository = mock(SkillSuiteVersionRepository.class);
        suiteMemberRepository = mock(SkillSuiteVersionMemberRepository.class);
        securityScanService = mock(SecurityScanService.class);
        when(securityScanService.isEnabled()).thenReturn(true);
        planner = new SkillSuiteBundlePreviewPlanner(
                namespaceRepository, skillRepository, skillVersionRepository, skillFileRepository,
                suiteRepository, suiteVersionRepository, suiteMemberRepository,
                new VisibilityChecker(), securityScanService,
                Clock.fixed(Instant.parse("2026-09-11T06:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void plansNewPackageAndCrossOwnerPublicReferenceWithoutPublishingEither() throws Exception {
        Namespace global = namespace(1L, "global");
        Namespace shared = namespace(2L, "shared");
        Skill referenced = skill(20L, 2L, "reference", "other-owner", SkillVisibility.PUBLIC, 200L);
        SkillVersion referencedVersion = version(200L, 20L, "2.0.0", SkillVersionStatus.PUBLISHED, true);
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global, shared));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "mixed-suite")).thenReturn(Optional.empty());
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(List.of(referenced));
        when(skillVersionRepository.findBySkillIdInAndStatus(anyList(), eq(SkillVersionStatus.PENDING_REVIEW)))
                .thenReturn(List.of());
        when(skillVersionRepository.findBySkillIdInAndVersionIn(anyList(), anyList()))
                .thenReturn(List.of(referencedVersion));
        when(skillVersionRepository.findByIdIn(anyList())).thenReturn(List.of(referencedVersion));
        when(skillFileRepository.findByVersionIdIn(anyList())).thenReturn(List.of());

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(manifest("mixed-suite", "CREATE", null, """
                            - skill: "@global/new-skill"
                              package:
                                path: skills/new-skill
                                visibility: PUBLIC
                            - skill: "@shared/reference"
                              reference:
                                version: 2.0.0
                        """, "@global/new-skill", true),
                        Map.of("skills/new-skill/SKILL.md", skillMd("new-skill", "1.0.0"))),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isTrue();
        assertThat(result.members()).extracting(SkillSuiteBundlePreviewPlanner.MemberPlan::publishAction)
                .containsExactly(
                        SkillSuiteBundlePublishAction.CREATE_SKILL,
                        SkillSuiteBundlePublishAction.REFERENCE_VERSION);
        assertThat(result.members().get(1).skillId()).isEqualTo(20L);
        verify(skillRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(skillVersionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksPendingReviewAndAnExistingNonPublishedTargetVersion() throws Exception {
        Namespace global = namespace(1L, "global");
        Skill existing = skill(10L, 1L, "owned", "actor", SkillVisibility.PUBLIC, 100L);
        SkillVersion current = version(100L, 10L, "1.0.0", SkillVersionStatus.PUBLISHED, true);
        SkillVersion target = version(101L, 10L, "2.0.0", SkillVersionStatus.UPLOADED, false);
        SkillVersion pending = version(102L, 10L, "1.5.0", SkillVersionStatus.PENDING_REVIEW, false);
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "blocked-suite")).thenReturn(Optional.empty());
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(List.of(existing));
        when(skillVersionRepository.findBySkillIdInAndStatus(anyList(), eq(SkillVersionStatus.PENDING_REVIEW)))
                .thenReturn(List.of(pending));
        when(skillVersionRepository.findBySkillIdInAndVersionIn(anyList(), anyList()))
                .thenReturn(List.of(target));
        when(skillVersionRepository.findByIdIn(anyList())).thenReturn(List.of(current));
        when(skillFileRepository.findByVersionIdIn(anyList())).thenReturn(List.of());

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(manifest("blocked-suite", "CREATE", null, packageMember("owned", false),
                                "@global/owned", true),
                        Map.of("skills/owned/SKILL.md", skillMd("owned", "2.0.0"))),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isFalse();
        assertThat(result.members()).singleElement().satisfies(member ->
                assertThat(member.errors())
                        .anyMatch(error -> error.contains("pending review"))
                        .anyMatch(error -> error.contains("non-published")));
    }

    @Test
    void keepsWarningsExplicitAndBoundToADeterministicDigest() throws Exception {
        Namespace global = namespace(1L, "global");
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "warning-suite")).thenReturn(Optional.empty());
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(List.of());
        String yaml = manifest("warning-suite", "CREATE", null,
                packageMember("warning-skill", true), "@global/warning-skill", true)
                .replace("visibility: PUBLIC", "visibility: PRIVATE");

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(yaml, Map.of(
                        "skills/warning-skill/SKILL.md", skillMd("warning-skill", "1.0.0"),
                        "skills/warning-skill/tool.exe", "not-an-executable")),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isTrue();
        assertThat(result.requiresWarningConfirmation()).isTrue();
        assertThat(result.warningDigest()).hasSize(64);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("tool.exe"));
    }

    @Test
    void suitePermissionDoesNotGrantPublishingPermissionForAnotherOwnersSkill() throws Exception {
        Namespace global = namespace(1L, "global");
        Skill otherOwnersSkill = skill(
                10L, 1L, "foreign", "other-owner", SkillVisibility.PUBLIC, 100L);
        SkillVersion current = version(100L, 10L, "1.0.0", SkillVersionStatus.PUBLISHED, true);
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "permission-suite")).thenReturn(Optional.empty());
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList()))
                .thenReturn(List.of(otherOwnersSkill));
        when(skillVersionRepository.findBySkillIdInAndStatus(anyList(), eq(SkillVersionStatus.PENDING_REVIEW)))
                .thenReturn(List.of());
        when(skillVersionRepository.findBySkillIdInAndVersionIn(anyList(), anyList())).thenReturn(List.of());
        when(skillVersionRepository.findByIdIn(anyList())).thenReturn(List.of(current));
        when(skillFileRepository.findByVersionIdIn(anyList())).thenReturn(List.of());

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(manifest("permission-suite", "CREATE", null,
                                packageMember("foreign", false), "@global/foreign", true),
                        Map.of("skills/foreign/SKILL.md", skillMd("foreign", "2.0.0"))),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isFalse();
        assertThat(result.members()).singleElement().satisfies(member ->
                assertThat(member.errors()).contains("No permission to publish existing Skill"));
    }

    @Test
    void blocksMemberVisibilityThatCannotServeTheSuiteAudience() throws Exception {
        Namespace global = namespace(1L, "global");
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "visibility-suite")).thenReturn(Optional.empty());
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(List.of());
        String yaml = manifest("visibility-suite", "CREATE", null,
                packageMember("private-member", true), "@global/private-member", true)
                .replace("      visibility: PUBLIC", "      visibility: PRIVATE");

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(yaml, Map.of(
                        "skills/private-member/SKILL.md", skillMd("private-member", "1.0.0"))),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isFalse();
        assertThat(result.members()).singleElement().satisfies(member ->
                assertThat(member.errors()).contains("New Skill visibility is incompatible with Suite audience"));
    }

    @Test
    void blocksExistingSuiteCoordinateBeforeAnyMemberWrite() throws Exception {
        Namespace global = namespace(1L, "global");
        SkillSuite existingSuite = mock(SkillSuite.class);
        when(existingSuite.getId()).thenReturn(50L);
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "existing-suite"))
                .thenReturn(Optional.of(existingSuite));
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(List.of());

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(manifest("existing-suite", "CREATE", null,
                                packageMember("member", true), "@global/member", true),
                        Map.of("skills/member/SKILL.md", skillMd("member", "1.0.0"))),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isFalse();
        assertThat(result.errors()).contains("Target Suite coordinate already exists");
        verify(skillRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksExistingTargetSuiteVersion() throws Exception {
        Namespace global = namespace(1L, "global");
        SkillSuite suite = mock(SkillSuite.class);
        when(suite.getId()).thenReturn(50L);
        when(suite.getNamespaceId()).thenReturn(1L);
        when(suite.getCreatedBy()).thenReturn("actor");
        when(suite.getStatus()).thenReturn(SkillSuiteStatus.ACTIVE);
        SkillSuiteVersion base = mock(SkillSuiteVersion.class);
        when(base.getId()).thenReturn(60L);
        when(base.getStatus()).thenReturn(SkillSuiteVersionStatus.PUBLISHED);
        when(base.getSummary()).thenReturn("Summary");
        when(base.getOverview()).thenReturn("Overview");
        SkillSuiteVersion existingTarget = mock(SkillSuiteVersion.class);
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "version-conflict"))
                .thenReturn(Optional.of(suite));
        when(suiteVersionRepository.findBySuiteIdAndVersion(50L, "1.0.0"))
                .thenReturn(Optional.of(base));
        when(suiteVersionRepository.findBySuiteIdAndVersion(50L, "1.1.0"))
                .thenReturn(Optional.of(existingTarget));
        when(suiteMemberRepository.findBySuiteVersionIdOrderByPosition(60L)).thenReturn(List.of());
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(List.of());

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(manifest("version-conflict", "UPDATE", "1.0.0",
                                packageMember("member", true), "@global/member", false),
                        Map.of("skills/member/SKILL.md", skillMd("member", "1.0.0"))),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isFalse();
        assertThat(result.errors()).contains("Target Suite version already exists");
    }

    @Test
    void updateInheritsPresentationAndReportsRemovedBaselineMembers() throws Exception {
        Namespace global = namespace(1L, "global");
        SkillSuite suite = mock(SkillSuite.class);
        when(suite.getId()).thenReturn(50L);
        when(suite.getNamespaceId()).thenReturn(1L);
        when(suite.getCreatedBy()).thenReturn("actor");
        when(suite.getStatus()).thenReturn(SkillSuiteStatus.ACTIVE);
        SkillSuiteVersion base = mock(SkillSuiteVersion.class);
        when(base.getId()).thenReturn(60L);
        when(base.getStatus()).thenReturn(SkillSuiteVersionStatus.PUBLISHED);
        when(base.getSummary()).thenReturn("Inherited summary");
        when(base.getOverview()).thenReturn("Inherited overview");
        Skill keptSkill = skill(10L, 1L, "kept", "other", SkillVisibility.PUBLIC, 100L);
        SkillVersion keptVersion = version(100L, 10L, "1.0.0", SkillVersionStatus.PUBLISHED, true);
        SkillSuiteVersionMember kept = baselineMember(60L, 10L, 100L, "global", "kept", "1.0.0", 0, true);
        SkillSuiteVersionMember removed = baselineMember(60L, 11L, 110L, "global", "removed", "1.0.0", 1, false);

        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "update-suite")).thenReturn(Optional.of(suite));
        when(suiteVersionRepository.findBySuiteIdAndVersion(50L, "1.0.0")).thenReturn(Optional.of(base));
        when(suiteVersionRepository.findBySuiteIdAndVersion(50L, "1.1.0")).thenReturn(Optional.empty());
        when(suiteMemberRepository.findBySuiteVersionIdOrderByPosition(60L)).thenReturn(List.of(kept, removed));
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(List.of(keptSkill));
        when(skillVersionRepository.findBySkillIdInAndStatus(anyList(), eq(SkillVersionStatus.PENDING_REVIEW)))
                .thenReturn(List.of());
        when(skillVersionRepository.findBySkillIdInAndVersionIn(anyList(), anyList()))
                .thenReturn(List.of(keptVersion));
        when(skillVersionRepository.findByIdIn(anyList())).thenReturn(List.of(keptVersion));
        when(skillFileRepository.findByVersionIdIn(anyList())).thenReturn(List.of());

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(manifest("update-suite", "UPDATE", "1.0.0", """
                            - skill: "@global/kept"
                              reference:
                                version: 1.0.0
                        """, "@global/kept", false), Map.of()),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isTrue();
        assertThat(result.summary()).isEqualTo("Inherited summary");
        assertThat(result.overview()).isEqualTo("Inherited overview");
        assertThat(result.members()).singleElement().satisfies(member ->
                assertThat(member.relationship()).isEqualTo(SkillSuiteBundleRelationshipChange.UNCHANGED));
        assertThat(result.removedMembers()).singleElement().satisfies(member ->
                assertThat(member.coordinate().canonical()).isEqualTo("@global/removed"));
        assertThat(result.removedMembers().getFirst().relationship())
                .isEqualTo(SkillSuiteBundleRelationshipChange.REMOVED);
        assertThat(result.removedMembers().getFirst().publishAction())
                .isEqualTo(SkillSuiteBundlePublishAction.NONE);
    }

    @Test
    void oneHundredExistingMembersUseOnlyBoundedBatchReads() throws Exception {
        Namespace global = namespace(1L, "global");
        StringBuilder memberYaml = new StringBuilder();
        Map<String, String> files = new LinkedHashMap<>();
        List<Skill> skills = new ArrayList<>();
        List<SkillVersion> versions = new ArrayList<>();
        List<SkillFile> storedFiles = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            String slug = "member-" + index;
            memberYaml.append(packageMember(slug, false));
            String path = "skills/" + slug + "/SKILL.md";
            String content = skillMd(slug, "1.0.0");
            files.put(path, content);
            long skillId = 1_000L + index;
            long versionId = 2_000L + index;
            skills.add(skill(skillId, 1L, slug, "actor", SkillVisibility.PUBLIC, versionId));
            versions.add(version(versionId, skillId, "1.0.0", SkillVersionStatus.PUBLISHED, true));
            storedFiles.add(skillFile(versionId, "SKILL.md", sha256(content)));
        }
        when(namespaceRepository.findBySlugIn(anyList())).thenReturn(List.of(global));
        when(suiteRepository.findByNamespaceIdAndSlug(1L, "large-suite")).thenReturn(Optional.empty());
        when(skillRepository.findByNamespaceIdInAndSlugIn(anyList(), anyList())).thenReturn(skills);
        when(skillVersionRepository.findBySkillIdInAndStatus(anyList(), eq(SkillVersionStatus.PENDING_REVIEW)))
                .thenReturn(List.of());
        when(skillVersionRepository.findBySkillIdInAndVersionIn(anyList(), anyList())).thenReturn(versions);
        when(skillVersionRepository.findByIdIn(anyList())).thenReturn(versions);
        when(skillFileRepository.findByVersionIdIn(anyList())).thenReturn(storedFiles);

        SkillSuiteBundlePreviewPlanner.PreviewPlan result = planner.plan(
                analyze(manifest("large-suite", "CREATE", null, memberYaml.toString(),
                        "@global/member-0", true), files),
                "actor", Map.of(1L, NamespaceRole.MEMBER), Set.of());

        assertThat(result.confirmable()).isTrue();
        assertThat(result.members()).hasSize(100)
                .allSatisfy(member -> assertThat(member.publishAction())
                        .isEqualTo(SkillSuiteBundlePublishAction.REUSE_VERSION));
        verify(namespaceRepository, times(1)).findBySlugIn(anyList());
        verify(skillRepository, times(1)).findByNamespaceIdInAndSlugIn(anyList(), anyList());
        verify(skillVersionRepository, times(1))
                .findBySkillIdInAndStatus(anyList(), eq(SkillVersionStatus.PENDING_REVIEW));
        verify(skillVersionRepository, times(1)).findBySkillIdInAndVersionIn(anyList(), anyList());
        verify(skillVersionRepository, times(1)).findByIdIn(anyList());
        verify(skillFileRepository, times(1)).findByVersionIdIn(anyList());
    }

    private SkillSuiteBundlePackageAnalyzer.BundleAnalysis analyze(
            String manifest, Map<String, String> memberFiles
    ) throws Exception {
        List<SkillSuiteBundleStagedEntry> entries = new ArrayList<>();
        entries.add(staged("SUITE.yaml", manifest));
        for (Map.Entry<String, String> file : memberFiles.entrySet()) {
            entries.add(staged(file.getKey(), file.getValue()));
        }
        SkillMetadataParser metadataParser = new SkillMetadataParser();
        return new SkillSuiteBundlePackageAnalyzer(
                new SkillSuiteBundleManifestParser(), metadataParser,
                new SkillPackageValidator(metadataParser)).analyze(entries);
    }

    private SkillSuiteBundleStagedEntry staged(String path, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SkillSuiteBundleStagedEntry(
                path, bytes.length, "text/plain", sha256(content), "temp/" + path,
                () -> new ByteArrayInputStream(bytes));
    }

    private Namespace namespace(Long id, String slug) {
        Namespace namespace = mock(Namespace.class);
        when(namespace.getId()).thenReturn(id);
        when(namespace.getSlug()).thenReturn(slug);
        when(namespace.getStatus()).thenReturn(NamespaceStatus.ACTIVE);
        return namespace;
    }

    private Skill skill(
            Long id, Long namespaceId, String slug, String owner,
            SkillVisibility visibility, Long latestVersionId
    ) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getNamespaceId()).thenReturn(namespaceId);
        when(skill.getSlug()).thenReturn(slug);
        when(skill.getOwnerId()).thenReturn(owner);
        when(skill.getVisibility()).thenReturn(visibility);
        when(skill.getStatus()).thenReturn(SkillStatus.ACTIVE);
        when(skill.getLatestVersionId()).thenReturn(latestVersionId);
        return skill;
    }

    private SkillVersion version(
            Long id, Long skillId, String value, SkillVersionStatus status, boolean downloadReady
    ) {
        SkillVersion version = mock(SkillVersion.class);
        when(version.getId()).thenReturn(id);
        when(version.getSkillId()).thenReturn(skillId);
        when(version.getVersion()).thenReturn(value);
        when(version.getStatus()).thenReturn(status);
        when(version.isDownloadReady()).thenReturn(downloadReady);
        return version;
    }

    private SkillFile skillFile(Long versionId, String path, String sha256) {
        SkillFile file = mock(SkillFile.class);
        when(file.getVersionId()).thenReturn(versionId);
        when(file.getFilePath()).thenReturn(path);
        when(file.getSha256()).thenReturn(sha256);
        return file;
    }

    private SkillSuiteVersionMember baselineMember(
            Long suiteVersionId, Long skillId, Long skillVersionId,
            String namespace, String slug, String version, int position, boolean entry
    ) {
        return new SkillSuiteVersionMember(
                suiteVersionId,
                new SkillSuiteMemberSelection(
                        skillId, skillVersionId, namespace, slug, version, "sha256:test"),
                position, entry);
    }

    private String manifest(
            String suiteSlug,
            String mode,
            String baseVersion,
            String members,
            String entry,
            boolean includePresentation
    ) {
        String base = baseVersion == null ? "" : "  baseVersion: " + baseVersion + "\n";
        String presentation = includePresentation
                ? "  summary: Suite summary\n  overview: Suite overview\n"
                : "";
        return """
                apiVersion: skillhub.iflytek.com/v1alpha1
                kind: SkillSuiteBundle
                metadata:
                  namespace: global
                  slug: %s
                spec:
                  mode: %s
                %s  version: %s
                  displayName: Test Suite
                %s  visibility: PUBLIC
                  entry: "%s"
                  members:
                %s
                """.formatted(suiteSlug, mode, base, "UPDATE".equals(mode) ? "1.1.0" : "1.0.0",
                presentation, entry, indent(members, 4));
    }

    private String packageMember(String slug, boolean visibility) {
        return "  - skill: \"@global/" + slug + "\"\n"
                + "    package:\n"
                + "      path: skills/" + slug + "\n"
                + (visibility ? "      visibility: PUBLIC\n" : "");
    }

    private String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return value.stripTrailing().lines().map(line -> prefix + line).collect(java.util.stream.Collectors.joining("\n"));
    }

    private String skillMd(String name, String version) {
        return """
                ---
                name: %s
                description: Skill description
                version: %s
                ---
                Instructions.
                """.formatted(name, version);
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
