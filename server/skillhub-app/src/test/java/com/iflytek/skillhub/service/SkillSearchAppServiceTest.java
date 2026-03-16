package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private SkillSearchAppService service;

    @BeforeEach
    void setUp() {
        service = new SkillSearchAppService(searchQueryService, skillRepository, namespaceRepository, skillVersionRepository);
    }

    @Test
    void search_shouldExcludeArchivedNamespaceSkillsForAnonymousUsers() {
        Skill archivedSkill = new Skill(1L, "archived-skill", "owner-1", SkillVisibility.PUBLIC);
        setField(archivedSkill, "id", 10L);

        Namespace archivedNamespace = new Namespace("archived-team", "Archived Team", "owner-1");
        setField(archivedNamespace, "id", 1L);
        archivedNamespace.setStatus(NamespaceStatus.ARCHIVED);

        when(searchQueryService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SearchResult(List.of(10L), 1, 0, 20));
        when(skillRepository.findByIdIn(List.of(10L))).thenReturn(List.of(archivedSkill));
        when(namespaceRepository.findByIdIn(List.of(1L))).thenReturn(List.of(archivedNamespace));

        SkillSearchAppService.SearchResponse response = service.search("archive", null, "newest", 0, 20, null, null);

        assertEquals(0, response.items().size());
        assertEquals(0, response.total());
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
