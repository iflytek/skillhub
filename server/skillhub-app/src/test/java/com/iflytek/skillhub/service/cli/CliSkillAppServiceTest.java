package com.iflytek.skillhub.service.cli;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.GithubSkillSourceResponse;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.cli.CliDeleteResponse;
import com.iflytek.skillhub.dto.cli.CliPublishResponse;
import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import com.iflytek.skillhub.repository.GithubSkillSourceQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CliSkillAppServiceTest {

    @Mock SkillSearchAppService skillSearchAppService;
    @Mock SkillQueryService skillQueryService;
    @Mock SkillDownloadService skillDownloadService;
    @Mock SkillDeleteAppService skillDeleteAppService;
    @Mock SkillPublishService skillPublishService;
    @Mock SkillRepository skillRepository;
    @Mock NamespaceRepository namespaceRepository;
    @Mock SkillVersionRepository skillVersionRepository;
    @Mock NamespaceService namespaceService;
    @Mock RbacService rbacService;

    private CliSkillAppService service;

    @BeforeEach
    void setUp() {
        service = new CliSkillAppService(
                skillSearchAppService, skillQueryService,
                skillDownloadService, skillDeleteAppService, skillPublishService);
    }

    @Test
    void search_mapsResultsToCliFormat() {
        var searchResponse = new SkillSearchAppService.SearchResponse(
                List.of(new SkillSummaryResponse(
                        1L, "pdf-parser", "PDF Parser", "Parse PDFs",
                        "PUBLIC", "ACTIVE", 100L, 5, BigDecimal.valueOf(4.5), 10,
                        "global", Instant.now(), null, null, false,
                        new SkillLifecycleVersionResponse(1L, "1.2.0", "PUBLISHED"),
                        new SkillLifecycleVersionResponse(1L, "1.2.0", "PUBLISHED"),
                        null, "PUBLISHED", null
                )),
                1L, 0, 20
        );
        given(skillSearchAppService.searchInstallableLatest("pdf", null, "newest", 0, 20, null, null))
                .willReturn(searchResponse);

        var result = service.search("pdf", 20, null, null);

        assertEquals(1, result.items().size());
        assertEquals("global", result.items().get(0).namespace());
        assertEquals("pdf-parser", result.items().get(0).slug());
        assertEquals("1.2.0", result.items().get(0).latestVersion());
        assertEquals("Parse PDFs", result.items().get(0).summary());
        assertEquals(1L, result.total());
        assertEquals(20, result.limit());
    }

    @Test
    void search_mapsInstallableSearchTotalFromQueryStage() {
        var searchResponse = new SkillSearchAppService.SearchResponse(
                List.of(
                        new SkillSummaryResponse(
                                2L, "ready", "Ready", "Installable",
                                "PUBLIC", "ACTIVE", 0L, 0, BigDecimal.ZERO, 0,
                                "global", Instant.now(), null, null, false,
                                new SkillLifecycleVersionResponse(2L, "1.0.0", "PUBLISHED"),
                                new SkillLifecycleVersionResponse(2L, "1.0.0", "PUBLISHED"),
                                null, "PUBLISHED", null
                        )
                ),
                1L, 0, 20
        );
        given(skillSearchAppService.searchInstallableLatest("demo", null, "newest", 0, 20, null, null))
                .willReturn(searchResponse);

        var result = service.search("demo", 20, null, null);

        assertEquals(1, result.items().size());
        assertEquals("ready", result.items().getFirst().slug());
        assertEquals(1L, result.total());
    }

    @Test
    void search_limitOneSkipsUninstallableMatchAndReturnsNextInstallableWithFilteredTotal() {
        Skill unavailableFirstMatch = new Skill(1L, "draft-first", "owner-1", SkillVisibility.PUBLIC);
        setField(unavailableFirstMatch, "id", 1L);
        assertLimitOneSkipsUninstallableFirstMatch(unavailableFirstMatch, List.of());
    }

    @Test
    void search_limitOneSkipsYankedLatestMatchAndReturnsNextInstallableWithFilteredTotal() {
        Skill unavailableFirstMatch = new Skill(1L, "yanked-first", "owner-1", SkillVisibility.PUBLIC);
        setField(unavailableFirstMatch, "id", 1L);
        unavailableFirstMatch.setLatestVersionId(10L);
        SkillVersion yanked = publishedVersion(1L, 10L, "1.0.0");
        yanked.setYankedAt(Instant.parse("2026-06-12T00:00:00Z"));

        assertLimitOneSkipsUninstallableFirstMatch(unavailableFirstMatch, List.of(yanked));
    }

    @Test
    void search_limitOneSkipsDownloadUnavailableLatestAndReturnsNextInstallableWithFilteredTotal() {
        Skill unavailableFirstMatch = new Skill(1L, "not-ready-first", "owner-1", SkillVisibility.PUBLIC);
        setField(unavailableFirstMatch, "id", 1L);
        unavailableFirstMatch.setLatestVersionId(10L);
        SkillVersion notReady = publishedVersion(1L, 10L, "1.0.0");
        notReady.setDownloadReady(false);

        assertLimitOneSkipsUninstallableFirstMatch(unavailableFirstMatch, List.of(notReady));
    }

    private void assertLimitOneSkipsUninstallableFirstMatch(
            Skill unavailableFirstMatch,
            List<SkillVersion> unavailableLatestVersions) {
        SearchQueryService rankedSearch = query -> requiresInstallableLatest(query)
                ? new SearchResult(List.of(2L), 1L, 0, 1)
                : new SearchResult(List.of(1L), 2L, 0, 1);
        SkillSearchAppService realSearchAppService = new SkillSearchAppService(
                rankedSearch,
                skillRepository,
                namespaceRepository,
                namespaceService,
                new SkillLifecycleProjectionService(skillVersionRepository),
                rbacService
        );
        CliSkillAppService realService = new CliSkillAppService(
                realSearchAppService,
                skillQueryService,
                skillDownloadService,
                skillDeleteAppService,
                skillPublishService
        );

        Skill installableSecondMatch = new Skill(1L, "ready-second", "owner-1", SkillVisibility.PUBLIC);
        setField(installableSecondMatch, "id", 2L);
        installableSecondMatch.setLatestVersionId(20L);

        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 1L);
        SkillVersion installableVersion = publishedVersion(2L, 20L, "1.0.0");

        org.mockito.Mockito.lenient()
                .when(skillRepository.findByIdIn(List.of(1L)))
                .thenReturn(List.of(unavailableFirstMatch));
        org.mockito.Mockito.lenient()
                .when(skillRepository.findByIdIn(List.of(2L)))
                .thenReturn(List.of(installableSecondMatch));
        org.mockito.Mockito.lenient()
                .when(namespaceRepository.findByIdIn(List.of(1L)))
                .thenReturn(List.of(namespace));
        org.mockito.Mockito.lenient()
                .when(skillVersionRepository.findByIdIn(List.of()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(skillVersionRepository.findByIdIn(List.of(10L)))
                .thenReturn(unavailableLatestVersions);
        org.mockito.Mockito.lenient()
                .when(skillVersionRepository.findByIdIn(List.of(20L)))
                .thenReturn(List.of(installableVersion));

        var result = realService.search("demo", 1, null, null);

        assertEquals(1, result.items().size());
        assertEquals("ready-second", result.items().getFirst().slug());
        assertEquals("1.0.0", result.items().getFirst().latestVersion());
        assertEquals(1L, result.total());
        assertEquals(1, result.limit());
    }

    @Test
    void resolve_delegatesToQueryService() {
        given(skillQueryService.resolveVersion("global", "demo", "2.0.0", null, null, "user-1", Map.of()))
                .willReturn(new SkillQueryService.ResolvedVersionDTO(
                        10L, "global", "demo", "2.0.0", 42L, "abc123", null,
                        "/api/v1/skills/global/demo/versions/2.0.0/download"
                ));

        CliResolveResponse response = service.resolve("global", "demo", "2.0.0", "user-1", Map.of());

        assertEquals("global", response.namespace());
        assertEquals("demo", response.slug());
        assertEquals("2.0.0", response.version());
        assertEquals(42L, response.versionId());
        assertEquals("abc123", response.fingerprint());
    }

    @Test
    void resolve_includesGithubSourceOnlyForSyncedVersions() {
        var sources = org.mockito.Mockito.mock(GithubSkillSourceQueryRepository.class);
        var withSources = new CliSkillAppService(skillSearchAppService, skillQueryService,
                skillDownloadService, skillDeleteAppService, skillPublishService, sources);
        var source = new GithubSkillSourceResponse("mattpocock", "skills", "skills/tdd", "a".repeat(40), 2,
                "RECOGNIZED", List.of(),
                new GithubSkillSourceResponse.UpstreamTag("AFTER_TAG", "REPOSITORY", "v1.3.2", "d".repeat(40), 3, null),
                null);
        given(skillQueryService.resolveVersion("github", "tdd", null, null, null, "user-1", Map.of()))
                .willReturn(new SkillQueryService.ResolvedVersionDTO(
                        10L, "github", "tdd", "0.0.0-gabc", 42L, "sha256:f", null, "/download"));
        given(skillQueryService.resolveVersion("global", "demo", null, null, null, "user-1", Map.of()))
                .willReturn(new SkillQueryService.ResolvedVersionDTO(
                        11L, "global", "demo", "1.0.0", 43L, "sha256:g", null, "/download"));
        given(sources.findPublishedSyncedVersion(42L)).willReturn(java.util.Optional.of(source));
        given(sources.findPublishedSyncedVersion(43L)).willReturn(java.util.Optional.empty());

        assertEquals(source, withSources.resolve("github", "tdd", null, "user-1", Map.of()).githubSource());
        assertNull(withSources.resolve("global", "demo", null, "user-1", Map.of()).githubSource());
    }

    @Test
    void listNamespaceSkills_mapsInstallableVersionsToCliManifest() {
        Skill skill = new Skill(7L, "demo", "user-1", SkillVisibility.NAMESPACE_ONLY);
        setField(skill, "id", 10L);
        skill.setLatestVersionId(42L);
        skill.setUpdatedBy("user-1");

        given(skillQueryService.listInstallableSkillsByNamespace(
                eq("team-a"), eq("user-1"), eq(Map.of()), eq(PageRequest.of(0, 100))))
                .willReturn(new PageImpl<>(List.of(skill), PageRequest.of(0, 100), 1));
        given(skillQueryService.resolveVersionById(42L, "user-1", Map.of(), java.util.Set.of()))
                .willReturn(new SkillQueryService.ResolvedVersionDTO(
                        10L, "team-a", "demo", "1.0.0", 42L, "sha256:fingerprint", null,
                        "/api/v1/skills/team-a/demo/versions/1.0.0/download"));

        var result = service.listNamespaceSkills("team-a", 0, 100, "user-1", Map.of());

        assertEquals(1, result.items().size());
        assertEquals("demo", result.items().getFirst().slug());
        assertEquals("1.0.0", result.items().getFirst().version());
        assertEquals("sha256:fingerprint", result.items().getFirst().fingerprint());
        assertNull(result.nextCursor());
    }

    @ParameterizedTest
    @CsvSource({
            "other-user, USER, true, false",
            "author, SKILL_ADMIN, false, false",
            "author, USER, true, true",
            "platform-admin, SUPER_ADMIN, false, true"
    })
    void actualCliDeleteEnforcesOwnerAndCurrentTeamMembership(String actor, String role,
                                                              boolean currentMember, boolean allowed) {
        var members = org.mockito.Mockito.mock(com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository.class);
        var hardDelete = org.mockito.Mockito.mock(com.iflytek.skillhub.domain.skill.service.SkillHardDeleteService.class);
        var searchIndex = org.mockito.Mockito.mock(com.iflytek.skillhub.search.SearchIndexService.class);
        var namespace = new Namespace("team-a", "Team A", "author");
        namespace.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.TEAM);
        var skill = new Skill(7L, "demo", "author", SkillVisibility.NAMESPACE_ONLY);
        setField(skill, "id", 11L);
        given(skillRepository.findByNamespaceSlugAndSlug("team-a", "demo")).willReturn(List.of(skill));
        org.mockito.Mockito.lenient().when(namespaceRepository.findById(7L)).thenReturn(java.util.Optional.of(namespace));
        org.mockito.Mockito.lenient().when(namespaceRepository.findLockedById(7L)).thenReturn(java.util.Optional.of(namespace));
        org.mockito.Mockito.lenient().when(members.findByNamespaceIdAndUserId(7L, actor)).thenReturn(currentMember
                ? java.util.Optional.of(new com.iflytek.skillhub.domain.namespace.NamespaceMember(7L, actor, NamespaceRole.MEMBER))
                : java.util.Optional.empty());
        var deleteService = new SkillDeleteAppService(
                new com.iflytek.skillhub.domain.skill.SkillOwnerAccessPolicy(namespaceRepository, members),
                skillRepository, namespaceRepository, members, hardDelete, searchIndex);
        var cli = new CliSkillAppService(skillSearchAppService, skillQueryService,
                skillDownloadService, deleteService, skillPublishService);
        var controller = new com.iflytek.skillhub.controller.cli.CliSkillController(cli,
                org.mockito.Mockito.mock(com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor.class),
                org.mockito.Mockito.mock(com.iflytek.skillhub.dto.ApiResponseFactory.class));
        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(actor, actor, null, null,
                "api_token", Set.of(role), Set.of("skill:delete"));
        var request = new org.springframework.mock.web.MockHttpServletRequest("DELETE", "/api/cli/v1/skills/team-a/demo");
        request.setRemoteAddr("127.0.0.1");

        if (allowed) {
            controller.deleteRemote("team-a", "demo", principal, request);
            org.mockito.Mockito.verify(hardDelete).hardDeleteSkill(skill, "team-a", actor, "127.0.0.1", null);
        } else {
            assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException.class,
                    () -> controller.deleteRemote("team-a", "demo", principal, request));
            org.mockito.Mockito.verifyNoInteractions(searchIndex, hardDelete);
        }
    }

    @Test
    void deleteRemote_delegatesToDeleteAppService() {
        var auditContext = new AuditRequestContext("127.0.0.1", "CLI/1.0");
        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(
                "user-1", "User", null, null, "api_token", Set.of("USER"), Set.of("skill:delete"));
        given(skillDeleteAppService.deleteSkillFromPortal("global", "demo", null, principal, auditContext))
                .willReturn(new SkillDeleteAppService.DeleteResult(10L, "global", "demo", true));

        CliDeleteResponse response = service.deleteRemote("global", "demo", principal, auditContext);

        assertTrue(response.ok());
        assertEquals("remote", response.scope());
        assertEquals("delete", response.action());
        assertEquals("global", response.namespace());
        assertEquals("demo", response.slug());
    }

    @Test
    void publish_delegatesToPublishService() {
        List<PackageEntry> entries = List.of(
                new PackageEntry("skillhub.yaml", "name: test".getBytes(), 10, "application/x-yaml")
        );
        var mockVersion = org.mockito.Mockito.mock(SkillVersion.class);
        given(mockVersion.getVersion()).willReturn("1.0.0");
        given(skillPublishService.publishFromEntries("global", entries, "user-1", SkillVisibility.PUBLIC, Set.of("USER"), false))
                .willReturn(new SkillPublishService.PublishResult(1L, "test-skill", mockVersion));

        CliPublishResponse response = service.publish("global", entries, "user-1", SkillVisibility.PUBLIC, Set.of("USER"));

        assertEquals("global", response.namespace());
        assertEquals("test-skill", response.slug());
        assertEquals("1.0.0", response.version());
        assertEquals("PUBLIC", response.visibility());
    }

    private boolean requiresInstallableLatest(SearchQuery query) {
        try {
            return (boolean) query.getClass().getMethod("requireInstallableLatest").invoke(query);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private SkillVersion publishedVersion(Long skillId, Long versionId, String versionNumber) {
        SkillVersion version = new SkillVersion(skillId, versionNumber, "owner-1");
        setField(version, "id", versionId);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        return version;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
