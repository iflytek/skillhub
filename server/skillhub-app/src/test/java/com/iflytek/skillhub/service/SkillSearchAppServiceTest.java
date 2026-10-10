package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import com.iflytek.skillhub.search.SearchVisibilityScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillSearchAppServiceTest {

    @Mock
    private SearchQueryService searchQueryService;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private NamespaceService namespaceService;

    @Mock
    private RbacService rbacService;

    @Mock
    private UserAccountRepository userAccountRepository;

    private SkillSearchAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillSearchAppService(
                searchQueryService,
                skillRepository,
                namespaceRepository,
                namespaceService,
                new SkillLifecycleProjectionService(skillVersionRepository),
                new ComplianceSnapshotProjectionService(new com.fasterxml.jackson.databind.ObjectMapper()),
                rbacService,
                userAccountRepository
        );
    }

    @Test
    void search_shouldExcludeArchivedNamespaceSkillsForAnonymousUsers() {
        when(searchQueryService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SearchResult(List.of(), 0, 0, 20));

        SkillSearchAppService.SearchResponse response = service.search("archive", null, "newest", 0, 20, null, null);

        assertEquals(0, response.items().size());
        assertEquals(0, response.total());
        verify(skillRepository, times(0)).findByIdIn(anyList());
    }

    @Test
    void search_shouldFillVisiblePageAcrossArchivedNamespaceResults() {
        Skill visibleSkill = new Skill(2L, "visible-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(visibleSkill, "id", 11L);
        visibleSkill.setLatestVersionId(111L);

        Namespace activeNamespace = new Namespace("team-a", "Team A", "owner-1");
        setField(activeNamespace, "id", 2L);
        activeNamespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SearchResult(List.of(11L), 1, 0, 20));
        when(skillRepository.findByIdIn(List.of(11L))).thenReturn(List.of(visibleSkill));
        when(namespaceRepository.findByIdIn(List.of(2L))).thenReturn(List.of(activeNamespace));
        when(skillVersionRepository.findByIdIn(List.of(111L))).thenReturn(List.of());
        when(userAccountRepository.findByIdIn(List.of("owner-1")))
                .thenReturn(List.of(new UserAccount("owner-1", "Alice", "alice@example.com", null)));

        SkillSearchAppService.SearchResponse response = service.search("skill", null, "newest", 0, 1, null, null);

        assertEquals(1, response.items().size());
        assertEquals("visible-skill", response.items().getFirst().slug());
        assertEquals("Alice", response.items().getFirst().ownerDisplayName());
        assertEquals(1, response.total());
        verify(searchQueryService, times(1)).search(any());
    }

    @Test
    void search_shouldHideArchivedNamespaceFilterForAnonymousUsers() {
        Namespace archivedNamespace = new Namespace("archived-team", "Archived Team", "owner-1");
        setField(archivedNamespace, "id", 1L);
        archivedNamespace.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceService.getNamespaceBySlugForRead("archived-team", null, Map.of())).thenThrow(
                new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                        "error.namespace.slug.notFound",
                        "archived-team"
                )
        );

        assertThrows(
                com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class,
                () -> service.search("skill", "archived-team", "newest", 0, 20, null, Map.of())
        );
    }

    @Test
    void search_shouldExcludeHiddenSkillsForRegularUsers() {
        Skill visibleSkill = new Skill(1L, "visible-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(visibleSkill, "id", 10L);
        visibleSkill.setLatestVersionId(101L);

        Skill hiddenSkill = new Skill(1L, "hidden-skill", "owner-2", SkillVisibility.PUBLIC);
        setField(hiddenSkill, "id", 11L);
        hiddenSkill.setLatestVersionId(102L);
        hiddenSkill.setHidden(true);

        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        setField(namespace, "id", 1L);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SearchResult(List.of(10L), 1, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(visibleSkill));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));
        when(skillVersionRepository.findByIdIn(List.of(101L))).thenReturn(List.of());

        SkillSearchAppService.SearchResponse response = service.search("skill", null, "newest", 0, 20, "user-9", Map.of());

        assertEquals(1, response.items().size());
        assertEquals("visible-skill", response.items().getFirst().slug());
        assertEquals(1, response.total());
    }

    @Test
    void search_shouldResolvePublishedVersionsInBatch() {
        Skill first = new Skill(1L, "skill-a", "owner-1", SkillVisibility.PUBLIC);
        setField(first, "id", 10L);
        first.setLatestVersionId(101L);
        Skill second = new Skill(1L, "skill-b", "owner-1", SkillVisibility.PUBLIC);
        setField(second, "id", 11L);
        second.setLatestVersionId(102L);

        SkillVersion firstVersion = publishedVersion(10L, 101L, "1.0.0");
        SkillVersion secondVersion = publishedVersion(11L, 102L, "2.0.0");

        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        setField(namespace, "id", 1L);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(10L, 11L), 2, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L, 11L))).thenReturn(List.of(first, second));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));
        when(skillVersionRepository.findByIdIn(List.of(101L, 102L))).thenReturn(List.of(firstVersion, secondVersion));

        SkillSearchAppService.SearchResponse response = service.search(null, null, "newest", 0, 20, null, null);

        assertEquals(2, response.items().size());
        assertEquals("1.0.0", response.items().get(0).publishedVersion().version());
        assertEquals("2.0.0", response.items().get(1).publishedVersion().version());
        verify(skillVersionRepository, times(1)).findByIdIn(List.of(101L, 102L));
        verify(skillVersionRepository, times(0))
                .findBySkillIdInAndStatus(List.of(10L, 11L), com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED);
    }

    @Test
    void search_shouldProjectComplianceSnapshotFromHeadlineVersion() {
        Skill skill = new Skill(1L, "compliance-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        skill.setLatestVersionId(101L);

        SkillVersion version = publishedVersion(10L, 101L, "1.0.0");
        version.setParsedMetadataJson("""
                {
                  "complianceSnapshot": {
                    "schemaVersion": "1.0",
                    "items": [
                      {
                        "standard": "mitre-attack",
                        "version": "v19.1",
                        "controlId": "T1059",
                        "title": "Command and Scripting Interpreter",
                        "evidence": []
                      }
                    ],
                    "digest": "sha256:demo"
                  }
                }
                """);

        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 1L);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(10L), 1, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(skill));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));
        when(skillVersionRepository.findByIdIn(List.of(101L))).thenReturn(List.of(version));

        SkillSearchAppService.SearchResponse response = service.search("T1059", null, "relevance", 0, 20, null, null);

        assertEquals(1, response.items().size());
        assertEquals("mitre-attack", response.items().getFirst().complianceSnapshot().items().getFirst().standard());
        assertEquals("T1059", response.items().getFirst().complianceSnapshot().items().getFirst().controlId());
        assertEquals("sha256:demo", response.items().getFirst().complianceSnapshot().digest());
    }

    @Test
    void search_shouldNotFallbackToOlderPublishedVersionWhenLatestIsMissing() {
        Skill skill = new Skill(1L, "missing-latest", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);

        SkillVersion oldInstallable = publishedVersion(10L, 100L, "0.9.0");

        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 1L);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(10L), 1, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(skill));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));
        org.mockito.Mockito.lenient()
                .when(skillVersionRepository.findBySkillIdInAndStatus(List.of(10L), SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of(oldInstallable));

        SkillSearchAppService.SearchResponse response = service.search(null, null, "newest", 0, 20, null, null);

        assertEquals(1, response.items().size());
        assertEquals("missing-latest", response.items().getFirst().slug());
        assertNull(response.items().getFirst().publishedVersion());
        verify(skillVersionRepository, times(0))
                .findBySkillIdInAndStatus(List.of(10L), SkillVersionStatus.PUBLISHED);
    }

    @Test
    void search_shouldNotFallbackToOlderPublishedVersionWhenLatestIsYanked() {
        Skill skill = new Skill(1L, "yanked-latest", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        skill.setLatestVersionId(101L);

        SkillVersion latest = publishedVersion(10L, 101L, "1.0.0");
        latest.setYankedAt(Instant.parse("2026-06-12T00:00:00Z"));
        SkillVersion oldInstallable = publishedVersion(10L, 100L, "0.9.0");

        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 1L);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(10L), 1, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(skill));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));
        when(skillVersionRepository.findByIdIn(List.of(101L))).thenReturn(List.of(latest));
        org.mockito.Mockito.lenient()
                .when(skillVersionRepository.findBySkillIdInAndStatus(List.of(10L), SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of(oldInstallable));

        SkillSearchAppService.SearchResponse response = service.search(null, null, "newest", 0, 20, null, null);

        assertEquals(1, response.items().size());
        assertEquals("yanked-latest", response.items().getFirst().slug());
        assertNull(response.items().getFirst().publishedVersion());
        verify(skillVersionRepository, times(0))
                .findBySkillIdInAndStatus(List.of(10L), SkillVersionStatus.PUBLISHED);
    }

    @Test
    void search_shouldNotFallbackToOlderPublishedVersionWhenLatestDownloadUnavailable() {
        Skill skill = new Skill(1L, "not-ready", "owner-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 10L);
        skill.setLatestVersionId(101L);

        SkillVersion version = new SkillVersion(10L, "1.0.0", "owner-1");
        setField(version, "id", 101L);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(false);
        SkillVersion oldInstallable = publishedVersion(10L, 100L, "0.9.0");

        Namespace namespace = new Namespace("global", "Global", "owner-1");
        setField(namespace, "id", 1L);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(10L), 1, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(skill));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));
        when(skillVersionRepository.findByIdIn(List.of(101L))).thenReturn(List.of(version));
        org.mockito.Mockito.lenient()
                .when(skillVersionRepository.findBySkillIdInAndStatus(List.of(10L), SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of(oldInstallable));

        SkillSearchAppService.SearchResponse response = service.search(null, null, "newest", 0, 20, null, null);

        assertEquals(1, response.items().size());
        assertEquals("not-ready", response.items().getFirst().slug());
        assertNull(response.items().getFirst().publishedVersion());
        verify(skillVersionRepository, times(0))
                .findBySkillIdInAndStatus(List.of(10L), SkillVersionStatus.PUBLISHED);
    }

    @Test
    void search_shouldNormalizeAndPassLabelSlugs() {
        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(), 0, 0, 20));

        service.search("skill", null, "newest", 0, 20, List.of(" Code-Generation ", "official", "official"), null, null);

        ArgumentCaptor<com.iflytek.skillhub.search.SearchQuery> captor =
                ArgumentCaptor.forClass(com.iflytek.skillhub.search.SearchQuery.class);
        verify(searchQueryService).search(captor.capture());
        assertEquals(List.of("code-generation", "official"), captor.getValue().labelSlugs());
    }

    @Test
    void search_shouldIncludeMemberNamespacesInVisibilityScope() {
        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(), 0, 0, 20));
        when(rbacService.getUserRoleCodes("user-9")).thenReturn(Set.of("USER"));

        service.search("skill", null, "newest", 0, 20, "user-9", Map.of(7L, NamespaceRole.MEMBER));

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchQueryService).search(captor.capture());

        SearchVisibilityScope scope = captor.getValue().visibilityScope();
        assertEquals("user-9", scope.userId());
        assertEquals(Set.of(7L), scope.memberNamespaceIds());
        assertEquals(Set.of(), scope.adminNamespaceIds());
        assertEquals(false, scope.platformWideAccess());
    }

    @Test
    void search_shouldNotGrantPlatformWideAccessToSuperAdminInPortal() {
        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(), 0, 0, 20));
        when(rbacService.getUserRoleCodes("admin-1")).thenReturn(Set.of("SUPER_ADMIN", "USER"));

        service.search("skill", null, "newest", 0, 20, "admin-1", Map.of());

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchQueryService).search(captor.capture());

        SearchVisibilityScope scope = captor.getValue().visibilityScope();
        assertEquals("admin-1", scope.userId());
        assertEquals(false, scope.platformWideAccess());
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

    private SkillVersion publishedVersion(Long skillId, Long versionId, String versionNumber) {
        SkillVersion version = new SkillVersion(skillId, versionNumber, "owner-1");
        setField(version, "id", versionId);
        version.setStatus(SkillVersionStatus.PUBLISHED);
        version.setDownloadReady(true);
        return version;
    }
}
