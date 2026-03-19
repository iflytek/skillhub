package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresSearchRebuildServiceTest {

    @Test
    void rebuildBySkill_shouldIndexFrontmatterFieldsAndKeywordsWithoutBody() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill skill = new Skill(7L, "smart-agent", "owner-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Smart Agent");
        skill.setSummary("Builds workflows");
        skill.setLatestVersionId(99L);

        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");

        SkillVersion version = new SkillVersion(1L, "1.2.0", "owner-1");
        version.setParsedMetadataJson("""
                {
                  "name": "Smart Agent",
                  "description": "Builds workflows",
                  "version": "1.2.0",
                  "body": "# ignored",
                  "frontmatter": {
                    "name": "Smart Agent",
                    "description": "Builds workflows",
                    "version": "1.2.0",
                    "author": "Jane Doe",
                    "tags": ["automation", "agentic"],
                    "keywords": ["workflow", "assistant"],
                    "config": {
                      "provider": "openai"
                    }
                  }
                }
                """);

        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(99L)).thenReturn(Optional.of(version));

        PostgresSearchRebuildService service = new PostgresSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                searchIndexService
        );

        service.rebuildBySkill(1L);

        ArgumentCaptor<SkillSearchDocument> captor = ArgumentCaptor.forClass(SkillSearchDocument.class);
        verify(searchIndexService).index(captor.capture());

        SkillSearchDocument document = captor.getValue();
        assertThat(document.keywords()).isEqualTo("agentic, assistant, automation, workflow");
        assertThat(document.searchText()).contains("Smart Agent");
        assertThat(document.searchText()).contains("smart-agent");
        assertThat(document.searchText()).contains("Builds workflows");
        assertThat(document.searchText()).contains("author");
        assertThat(document.searchText()).contains("Jane Doe");
        assertThat(document.searchText()).contains("config");
        assertThat(document.searchText()).contains("provider");
        assertThat(document.searchText()).contains("openai");
        assertThat(document.searchText()).doesNotContain("# ignored");
    }
}
