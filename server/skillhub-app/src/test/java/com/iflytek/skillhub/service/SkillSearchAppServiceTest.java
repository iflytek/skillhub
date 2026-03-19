package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private SkillSearchAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillSearchAppService(
                searchQueryService,
                skillRepository,
                namespaceRepository,
                namespaceService,
                new SkillLifecycleProjectionService(skillVersionRepository)
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
        when(skillVersionRepository.findBySkillIdInAndStatus(List.of(11L), com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());

        SkillSearchAppService.SearchResponse response = service.search("skill", null, "newest", 0, 1, null, null);

        assertEquals(1, response.items().size());
        assertEquals("visible-skill", response.items().getFirst().slug());
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
        when(skillVersionRepository.findBySkillIdInAndStatus(List.of(10L), com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());

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

        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        setField(namespace, "id", 1L);
        namespace.setStatus(NamespaceStatus.ACTIVE);

        when(searchQueryService.search(any()))
                .thenReturn(new SearchResult(List.of(10L, 11L), 2, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L, 11L))).thenReturn(List.of(first, second));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(namespace));
        when(skillVersionRepository.findByIdIn(List.of(101L, 102L))).thenReturn(List.of());
        when(skillVersionRepository.findBySkillIdInAndStatus(List.of(10L, 11L), com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED))
                .thenReturn(List.of());

        SkillSearchAppService.SearchResponse response = service.search(null, null, "newest", 0, 20, null, null);

        assertEquals(2, response.items().size());
        verify(skillVersionRepository, times(1)).findByIdIn(List.of(101L, 102L));
        verify(skillVersionRepository, times(1))
                .findBySkillIdInAndStatus(List.of(10L, 11L), com.iflytek.skillhub.domain.skill.SkillVersionStatus.PUBLISHED);
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
