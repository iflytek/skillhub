package com.iflytek.skillhub.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.support.MultipartPackageExtractor;
import com.iflytek.skillhub.controller.support.ZipPackageExtractor;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService.PublishResult;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.social.SkillStarService;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class ClawHubCompatAppServiceTest {

    private final SkillSearchAppService skillSearchAppService = mock(SkillSearchAppService.class);
    private final SkillQueryService skillQueryService = mock(SkillQueryService.class);
    private final SkillPublishService skillPublishService = mock(SkillPublishService.class);
    private final ZipPackageExtractor zipPackageExtractor = mock(ZipPackageExtractor.class);
    private final MultipartPackageExtractor multipartPackageExtractor = mock(MultipartPackageExtractor.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final CompatSkillLookupService compatSkillLookupService = mock(CompatSkillLookupService.class);
    private final SkillStarService skillStarService = mock(SkillStarService.class);

    private final ClawHubCompatAppService service = new ClawHubCompatAppService(
            new CanonicalSlugMapper(),
            skillSearchAppService,
            skillQueryService,
            skillPublishService,
            zipPackageExtractor,
            multipartPackageExtractor,
            auditLogService,
            compatSkillLookupService,
            skillStarService
    );

    @Test
    void downloadLocationByQuery_throwsNotFound_whenLegacySkillIsPrivateForAnonymousCaller() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill privateSkill = new Skill(1L, "priv", "owner-1", SkillVisibility.PRIVATE);
        CompatSkillLookupService.CompatSkillContext context = new CompatSkillLookupService.CompatSkillContext(
                namespace,
                privateSkill,
                Optional.empty()
        );

        when(compatSkillLookupService.findByLegacySlug("priv")).thenReturn(context);
        when(compatSkillLookupService.canAccess(privateSkill, null, Map.of())).thenReturn(false);

        assertThatThrownBy(() -> service.downloadLocationByQuery("priv", "latest", null, null))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void downloadLocationByQuery_returnsCanonicalPath_whenLegacySkillIsVisible() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill publicSkill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        CompatSkillLookupService.CompatSkillContext context = new CompatSkillLookupService.CompatSkillContext(
                namespace,
                publicSkill,
                Optional.empty()
        );

        when(compatSkillLookupService.findByLegacySlug("my-skill")).thenReturn(context);
        when(compatSkillLookupService.canAccess(publicSkill, null, Map.of())).thenReturn(true);

        String location = service.downloadLocationByQuery("my-skill", "latest", null, null);

        assertThat(location).isEqualTo("/api/v1/skills/team-a/my-skill/download");
    }

    @Test
    void publishSkill_usesNamespaceDerivedFromCanonicalSlugWhenPayloadNamespaceMissing() throws Exception {
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                null, "team-a--demo", "Demo", "1.0.0", null, true, null, null);
        MultipartPackageExtractor.ExtractedPackage extracted = new MultipartPackageExtractor.ExtractedPackage(
                payload,
                java.util.List.of(new PackageEntry("SKILL.md", "x".getBytes(StandardCharsets.UTF_8), 1, "text/markdown"))
        );
        SkillVersion version = new SkillVersion(10L, "1.0.0", "user-1");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 33L);
        when(multipartPackageExtractor.extract(null, "{\"slug\":\"team-a--demo\"}")).thenReturn(extracted);
        when(skillPublishService.publishFromEntries("team-a", extracted.entries(), "user-1", SkillVisibility.PUBLIC, Set.of("PUBLISHER"), false))
                .thenReturn(new PublishResult(10L, "demo", version));

        var response = service.publishSkill(
                "{\"slug\":\"team-a--demo\"}",
                null,
                false,
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of("PUBLISHER")),
                "203.0.113.10",
                "JUnit"
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("10");
        assertThat(response.versionId()).isEqualTo("33");
    }

    @Test
    void starSkill_returnsAlreadyStarredFlag() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        when(compatSkillLookupService.resolveVisible("team-a", "my-skill", "user-1"))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(skillStarService.isStarred(42L, "user-1")).thenReturn(true);

        var response = service.starSkill(
                "team-a--my-skill",
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of())
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.alreadyStarred()).isTrue();
        verify(skillStarService).star(42L, "user-1");
    }

    @Test
    void unstarSkill_returnsAlreadyUnstarredWhenSkillWasNotStarred() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        when(compatSkillLookupService.resolveVisible("team-a", "my-skill", "user-1"))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(skillStarService.isStarred(42L, "user-1")).thenReturn(false);

        var response = service.unstarSkill(
                "team-a--my-skill",
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of())
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.alreadyUnstarred()).isTrue();
        verify(skillStarService).unstar(42L, "user-1");
    }

    @Test
    void whoami_mapsPrincipalFields() {
        var response = service.whoami(new PlatformPrincipal("user-1", "User", null, "avatar.png", null, Set.of()));

        assertThat(response.user().handle()).isEqualTo("user-1");
        assertThat(response.user().displayName()).isEqualTo("User");
        assertThat(response.user().image()).isEqualTo("avatar.png");
    }

    @Test
    void search_withNullQ_usesNewestSort() {
        when(skillSearchAppService.search(null, null, "newest", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        var response = service.search(null, 0, 20, null, null);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void search_withBlankQ_usesNewestSort() {
        when(skillSearchAppService.search("  ", null, "newest", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        var response = service.search("  ", 0, 20, null, null);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void search_withNonBlankQ_usesRelevanceSort() {
        when(skillSearchAppService.search("agent", null, "relevance", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 20));

        var response = service.search("agent", 0, 20, null, null);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void resolveByQuery_withLatestVersion_passesNullVersion() {
        when(skillQueryService.resolveVersion("global", "my-skill", null, "latest", "hash", null, Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(1L, "global", "my-skill", "latest", 2L, "sha", true, "/dl"));

        var response = service.resolveByQuery("global--my-skill", "latest", "hash", null, null);
        assertThat(response.match().version()).isEqualTo("latest");
    }

    @Test
    void resolveByQuery_withSpecificVersion_passesVersion() {
        when(skillQueryService.resolveVersion("global", "my-skill", "1.0.0", null, "hash", null, Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(1L, "global", "my-skill", "1.0.0", 2L, "sha", true, "/dl"));

        var response = service.resolveByQuery("global--my-skill", "1.0.0", "hash", null, null);
        assertThat(response.match().version()).isEqualTo("1.0.0");
    }

    @Test
    void resolve_withNullUserNsRoles_usesEmptyMap() {
        when(skillQueryService.resolveVersion("global", "my-skill", null, "latest", null, "user-1", Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(1L, "global", "my-skill", "latest", 2L, "sha", true, "/dl"));

        var response = service.resolve("global--my-skill", "latest", "user-1", null);
        assertThat(response.match().version()).isEqualTo("latest");
    }

    @Test
    void resolve_withNonNullUserNsRoles_passesRoles() {
        when(skillQueryService.resolveVersion("global", "my-skill", null, "latest", null, "user-1", Map.of(1L, com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN)))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(1L, "global", "my-skill", "latest", 2L, "sha", true, "/dl"));

        var response = service.resolve("global--my-skill", "latest", "user-1", Map.of(1L, com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN));
        assertThat(response.match().version()).isEqualTo("latest");
    }

    @Test
    void downloadLocationByPath_withLatest_returnsLatestPath() {
        String location = service.downloadLocationByPath("team-a--demo", "latest");
        assertThat(location).isEqualTo("/api/v1/skills/team-a/demo/download");
    }

    @Test
    void downloadLocationByPath_withSpecificVersion_returnsVersionedPath() {
        String location = service.downloadLocationByPath("team-a--demo", "1.0.0");
        assertThat(location).isEqualTo("/api/v1/skills/team-a/demo/versions/1.0.0/download");
    }

    @Test
    void downloadLocationByQuery_withSpecificVersion_returnsVersionedPath() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill publicSkill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        CompatSkillLookupService.CompatSkillContext context = new CompatSkillLookupService.CompatSkillContext(
                namespace, publicSkill, Optional.empty());

        when(compatSkillLookupService.findByLegacySlug("my-skill")).thenReturn(context);
        when(compatSkillLookupService.canAccess(publicSkill, null, Map.of())).thenReturn(true);

        String location = service.downloadLocationByQuery("my-skill", "1.0.0", null, null);
        assertThat(location).isEqualTo("/api/v1/skills/team-a/my-skill/versions/1.0.0/download");
    }

    @Test
    void resolveQueryCoordinate_withLegacySlugNotFound_fallsBackToCanonical() {
        when(compatSkillLookupService.findByLegacySlug("team-a--demo"))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "team-a--demo"));

        String location = service.downloadLocationByQuery("team-a--demo", "latest", null, null);
        assertThat(location).isEqualTo("/api/v1/skills/team-a/demo/download");
    }

    @Test
    void listSkills_withNullSort_defaultsToNewest() {
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 25));

        var response = service.listSkills(0, 25, null, null, null);
        assertThat(response.items()).isEmpty();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void listSkills_withMoreResults_returnsNextCursor() {
        SkillSummaryResponse item = new SkillSummaryResponse(
                1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE", 0L, 0, java.math.BigDecimal.ZERO, 0,
                "global", Instant.parse("2026-03-18T09:00:00Z"), false,
                null, null, null, null);
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(item), 100, 0, 25));

        var response = service.listSkills(0, 25, null, null, null);
        assertThat(response.nextCursor()).isEqualTo("1");
    }

    @Test
    void listSkills_withNoMoreResults_returnsNullCursor() {
        SkillSummaryResponse item = new SkillSummaryResponse(
                1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE", 0L, 0, java.math.BigDecimal.ZERO, 0,
                "global", Instant.parse("2026-03-18T09:00:00Z"), false,
                null, null, null, null);
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(item), 1, 0, 25));

        var response = service.listSkills(0, 25, null, null, null);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void getSkill_withTwoArgs_delegatesToThreeArgs() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        skill.setLatestVersionId(7L);
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.of(version)));

        var response = service.getSkill("team-a--demo", "user-1");
        assertThat(response.skill().slug()).isEqualTo("team-a--demo");
    }

    @Test
    void getSkill_withNullUserNsRoles_usesEmptyMap() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));

        var response = service.getSkill("team-a--demo", "user-1", null);
        assertThat(response.skill().slug()).isEqualTo("team-a--demo");
    }

    @Test
    void getSkill_withNullSkillId_returnsNullSkillInfo() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));

        var response = service.getSkill("team-a--demo", "user-1", Map.of());
        assertThat(response.skill()).isNull();
    }

    @Test
    void getSkill_withNullTimestamps_returnsZeroEpoch() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));

        var response = service.getSkill("team-a--demo", "user-1", Map.of());
        assertThat(response.skill().createdAt()).isZero();
        assertThat(response.skill().updatedAt()).isZero();
    }

    @Test
    void getSkill_withLatestVersionAndNullChangelog_returnsEmptyChangelog() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        skill.setLatestVersionId(7L);
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        version.setChangelog(null);
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.of(version)));

        var response = service.getSkill("team-a--demo", "user-1", Map.of());
        assertThat(response.latestVersion().changelog()).isEmpty();
    }

    @Test
    void deleteSkill_returnsOkResponse() {
        var response = service.deleteSkill();
        assertThat(response.ok()).isTrue();
    }

    @Test
    void undeleteSkill_returnsOkResponse() {
        var response = service.undeleteSkill();
        assertThat(response.ok()).isTrue();
    }

    @Test
    void publish_withZipFile_returnsPublishResponse() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        SkillVersion version = new SkillVersion(10L, "1.0.0", "user-1");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 33L);
        var entries = List.of(
                new com.iflytek.skillhub.domain.skill.validation.PackageEntry("SKILL.md", "x".getBytes(StandardCharsets.UTF_8), 1, "text/markdown"));
        when(zipPackageExtractor.extract(file)).thenReturn(entries);
        when(skillPublishService.publishFromEntries(eq("global"), anyList(), eq("user-1"), eq(SkillVisibility.PUBLIC), eq(Set.of("PUBLISHER")), eq(false)))
                .thenReturn(new SkillPublishService.PublishResult(10L, "demo", version));

        var response = service.publish(file, "global", false,
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of("PUBLISHER")), "203.0.113.10", "JUnit");

        assertThat(response.ok()).isTrue();
        assertThat(response.skillId()).isEqualTo("10");
    }

    @Test
    void toSearchResult_withNullUpdatedAt_returnsNullUpdatedAt() {
        when(skillSearchAppService.search("", null, "newest", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(
                        new SkillSummaryResponse(1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE", 0L, 0, java.math.BigDecimal.ZERO, 0,
                                "global", null, false, null, null, null, null)), 1, 0, 20));

        var response = service.search("", 0, 20, null, null);
        assertThat(response.results().get(0).updatedAt()).isNull();
    }

    @Test
    void toSearchResult_withNullPublishedVersion_returnsNullVersion() {
        when(skillSearchAppService.search("", null, "newest", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(
                        new SkillSummaryResponse(1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE", 0L, 0, java.math.BigDecimal.ZERO, 0,
                                "global", Instant.now(), false, null, null, null, null)), 1, 0, 20));

        var response = service.search("", 0, 20, null, null);
        assertThat(response.results().get(0).version()).isNull();
    }

    @Test
    void calculateScore_withNullCounts_returnsZero() {
        when(skillSearchAppService.search("", null, "newest", 0, 20, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(
                        new SkillSummaryResponse(1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE", null, null, java.math.BigDecimal.ZERO, 0,
                                "global", Instant.now(), false, null, null, null, null)), 1, 0, 20));

        var response = service.search("", 0, 20, null, null);
        assertThat(response.results().get(0).score()).isZero();
    }

    @Test
    void toResolveResponse_withNullVersion_returnsNullFields() {
        when(skillQueryService.resolveVersion("global", "my-skill", null, "latest", null, null, Map.of()))
                .thenReturn(new SkillQueryService.ResolvedVersionDTO(1L, "global", "my-skill", null, 2L, "sha", true, "/dl"));

        var response = service.resolve("global--my-skill", "latest", null, null);
        assertThat(response.match()).isNull();
        assertThat(response.latestVersion()).isNull();
    }

    @Test
    void determineNamespace_withNullPayload_returnsGlobal() throws Exception {
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                null, "my-skill", "Demo", "1.0.0", null, true, null, null);
        MultipartPackageExtractor.ExtractedPackage extracted = new MultipartPackageExtractor.ExtractedPackage(
                payload, List.of());
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-1");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 22L);
        when(multipartPackageExtractor.extract(null, "{}")).thenReturn(extracted);
        when(skillPublishService.publishFromEntries("global", List.of(), "user-1", SkillVisibility.PUBLIC, Set.of(), false))
                .thenReturn(new SkillPublishService.PublishResult(1L, "demo", version));

        var response = service.publishSkill("{}", null, false,
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of()), "127.0.0.1", "test");
        assertThat(response.ok()).isTrue();
    }

    @Test
    void normalizeNamespace_withoutAtPrefix_returnsTrimmed() throws Exception {
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                "team-explicit", "my-skill", "Demo", "1.0.0", null, true, null, null);
        MultipartPackageExtractor.ExtractedPackage extracted = new MultipartPackageExtractor.ExtractedPackage(
                payload, List.of());
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-1");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 23L);
        when(multipartPackageExtractor.extract(null, "{}")).thenReturn(extracted);
        when(skillPublishService.publishFromEntries("team-explicit", List.of(), "user-1", SkillVisibility.PUBLIC, Set.of(), false))
                .thenReturn(new SkillPublishService.PublishResult(1L, "demo", version));

        var response = service.publishSkill("{}", null, false,
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of()), "127.0.0.1", "test");
        assertThat(response.ok()).isTrue();
    }

    @Test
    void downloadLocationByQuery_withNullSlug_usesLegacyLookup() {
        Namespace namespace = new Namespace("global", "Global", "owner-1");
        Skill skill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        when(compatSkillLookupService.findByLegacySlug(null))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(compatSkillLookupService.canAccess(skill, null, Map.of())).thenReturn(true);

        String location = service.downloadLocationByQuery(null, "latest", null, null);
        assertThat(location).isEqualTo("/api/v1/skills/global/my-skill/download");
    }

    @Test
    void downloadLocationByQuery_withLegacySlugNotFound_fallsBackToCanonical_withoutDoubleDash() {
        when(compatSkillLookupService.findByLegacySlug("missing"))
                .thenThrow(new DomainNotFoundException("error.skill.notFound", "missing"));

        String location = service.downloadLocationByQuery("missing", "latest", null, null);
        assertThat(location).isEqualTo("/api/v1/skills/global/missing/download");
    }

    @Test
    void listSkills_withExplicitSort_usesProvidedSort() {
        when(skillSearchAppService.search("", null, "popular", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(), 0, 0, 25));

        var response = service.listSkills(0, 25, "popular", null, null);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void getSkill_withNonNullTimestamps_returnsEpochMillis() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "createdAt", Instant.parse("2026-03-18T08:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "updatedAt", Instant.parse("2026-03-18T09:00:00Z"));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));

        var response = service.getSkill("team-a--demo", "user-1", Map.of());
        assertThat(response.skill().createdAt()).isEqualTo(Instant.parse("2026-03-18T08:00:00Z").toEpochMilli());
        assertThat(response.skill().updatedAt()).isEqualTo(Instant.parse("2026-03-18T09:00:00Z").toEpochMilli());
    }

    @Test
    void getSkill_withNonNullPublishedAtAndChangelog() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "demo", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        skill.setLatestVersionId(7L);
        SkillVersion version = new SkillVersion(1L, "1.0.0", "owner-1");
        version.setChangelog("Fixed bugs");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "publishedAt", Instant.parse("2026-03-20T10:00:00Z"));
        when(compatSkillLookupService.resolveVisible("team-a", "demo", "user-1", Map.of()))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.of(version)));

        var response = service.getSkill("team-a--demo", "user-1", Map.of());
        assertThat(response.latestVersion().version()).isEqualTo("1.0.0");
        assertThat(response.latestVersion().createdAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z").toEpochMilli());
        assertThat(response.latestVersion().changelog()).isEqualTo("Fixed bugs");
    }

    @Test
    void unstarSkill_whenStarred_returnsNotAlreadyUnstarred() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        Skill skill = new Skill(1L, "my-skill", "owner-1", SkillVisibility.PUBLIC);
        org.springframework.test.util.ReflectionTestUtils.setField(skill, "id", 42L);
        when(compatSkillLookupService.resolveVisible("team-a", "my-skill", "user-1"))
                .thenReturn(new CompatSkillLookupService.CompatSkillContext(namespace, skill, Optional.empty()));
        when(skillStarService.isStarred(42L, "user-1")).thenReturn(true);

        var response = service.unstarSkill("team-a--my-skill",
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of()));

        assertThat(response.ok()).isTrue();
        assertThat(response.alreadyUnstarred()).isFalse();
    }

    @Test
    void listSkills_withNullUpdatedAt_returnsZeroUpdatedAt() {
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(
                        new SkillSummaryResponse(1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE",
                                0L, 0, java.math.BigDecimal.ZERO, 0,
                                "global", null, false,
                                new com.iflytek.skillhub.dto.SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                null, null, null)), 1, 0, 25));

        var response = service.listSkills(0, 25, null, null, null);
        assertThat(response.items().get(0).updatedAt()).isZero();
    }

    @Test
    void listSkills_withNullPublishedVersion_returnsNullLatestVersion() {
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(
                        new SkillSummaryResponse(1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE",
                                0L, 0, java.math.BigDecimal.ZERO, 0,
                                "global", Instant.now(), false,
                                null, null, null, null)), 1, 0, 25));

        var response = service.listSkills(0, 25, null, null, null);
        assertThat(response.items().get(0).latestVersion()).isNull();
    }

    @Test
    void listSkills_withPublishedVersion_returnsLatestVersion() {
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(
                        new SkillSummaryResponse(1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE",
                                0L, 0, java.math.BigDecimal.ZERO, 0,
                                "global", Instant.parse("2026-03-18T09:00:00Z"), false,
                                null,
                                new com.iflytek.skillhub.dto.SkillLifecycleVersionResponse(11L, "1.0.0", "PUBLISHED"),
                                null, null)), 1, 0, 25));

        var response = service.listSkills(0, 25, null, null, null);
        assertThat(response.items().get(0).latestVersion().version()).isEqualTo("1.0.0");
    }

    @Test
    void listSkills_withNullCounts_returnsEmptyStats() {
        when(skillSearchAppService.search("", null, "newest", 0, 25, null, null))
                .thenReturn(new SkillSearchAppService.SearchResponse(List.of(
                        new SkillSummaryResponse(1L, "demo", "Demo", "summary", "PUBLIC", "ACTIVE",
                                null, null, java.math.BigDecimal.ZERO, 0,
                                "global", Instant.now(), false,
                                null, null, null, null)), 1, 0, 25));

        var response = service.listSkills(0, 25, null, null, null);
        assertThat(response.items().get(0).stats()).isEqualTo(Map.of());
    }

    @Test
    void determineNamespace_withCanonicalSlugInPayload_returnsNamespaceFromSlug() throws Exception {
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                null, "team-a--demo", "Demo", "1.0.0", null, true, null, null);
        MultipartPackageExtractor.ExtractedPackage extracted = new MultipartPackageExtractor.ExtractedPackage(
                payload, List.of());
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-1");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 24L);
        when(multipartPackageExtractor.extract(null, "{}")).thenReturn(extracted);
        when(skillPublishService.publishFromEntries("team-a", List.of(), "user-1", SkillVisibility.PUBLIC, Set.of(), false))
                .thenReturn(new SkillPublishService.PublishResult(1L, "demo", version));

        var response = service.publishSkill("{}", null, false,
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of()), "127.0.0.1", "test");
        assertThat(response.ok()).isTrue();
    }

    @Test
    void determineNamespace_withPlainSlug_returnsGlobal() throws Exception {
        MultipartPackageExtractor.PublishPayload payload = new MultipartPackageExtractor.PublishPayload(
                null, "my-skill", "Demo", "1.0.0", null, true, null, null);
        MultipartPackageExtractor.ExtractedPackage extracted = new MultipartPackageExtractor.ExtractedPackage(
                payload, List.of());
        SkillVersion version = new SkillVersion(1L, "1.0.0", "user-1");
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", 25L);
        when(multipartPackageExtractor.extract(null, "{}")).thenReturn(extracted);
        when(skillPublishService.publishFromEntries("global", List.of(), "user-1", SkillVisibility.PUBLIC, Set.of(), false))
                .thenReturn(new SkillPublishService.PublishResult(1L, "demo", version));

        var response = service.publishSkill("{}", null, false,
                new PlatformPrincipal("user-1", "User", null, null, null, Set.of()), "127.0.0.1", "test");
        assertThat(response.ok()).isTrue();
    }

    @Test
    void determineNamespace_withNullPayloadViaReflection_returnsGlobal() {
        String namespace = (String) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "determineNamespace", (MultipartPackageExtractor.PublishPayload) null);
        assertThat(namespace).isEqualTo("global");
    }
}
