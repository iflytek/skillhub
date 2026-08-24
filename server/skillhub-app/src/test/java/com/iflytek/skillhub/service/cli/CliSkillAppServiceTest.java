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
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.cli.CliDeleteResponse;
import com.iflytek.skillhub.dto.cli.CliPublishResponse;
import com.iflytek.skillhub.dto.cli.CliResolveResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SkillDeleteAppService;
import com.iflytek.skillhub.service.SkillSearchAppService;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void deleteRemote_delegatesToDeleteAppService() {
        var auditContext = new AuditRequestContext("127.0.0.1", "CLI/1.0");
        given(skillDeleteAppService.deleteSkill("global", "demo", null, "user-1", auditContext))
                .willReturn(new SkillDeleteAppService.DeleteResult(10L, "global", "demo", true));

        CliDeleteResponse response = service.deleteRemote("global", "demo", "user-1", auditContext);

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
