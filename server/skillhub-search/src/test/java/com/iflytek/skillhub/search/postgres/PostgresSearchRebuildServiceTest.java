package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
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
                searchIndexService,
                new SearchTextTokenizer()
        );

        service.rebuildBySkill(1L);

        ArgumentCaptor<SkillSearchDocument> captor = ArgumentCaptor.forClass(SkillSearchDocument.class);
        verify(searchIndexService).index(captor.capture());

        SkillSearchDocument document = captor.getValue();
        assertThat(document.title()).isEqualTo("Smart Agent");
        assertThat(document.summary()).isEqualTo("Builds workflows");
        assertThat(document.keywords()).contains("agentic");
        assertThat(document.keywords()).contains("assistant");
        assertThat(document.keywords()).contains("automation");
        assertThat(document.keywords()).contains("workflow");
        assertThat(document.searchText()).contains("smart-agent");
        assertThat(document.searchText()).contains("Builds workflows");
        assertThat(document.searchText()).contains("author");
        assertThat(document.searchText()).contains("Jane Doe");
        assertThat(document.searchText()).contains("config");
        assertThat(document.searchText()).contains("provider");
        assertThat(document.searchText()).contains("openai");
        assertThat(document.searchText()).doesNotContain("agentic");
        assertThat(document.searchText()).doesNotContain("assistant");
        assertThat(document.searchText()).doesNotContain("# ignored");
    }

    @Test
    void rebuildBySkill_shouldUseLatestVersionMetadataWhenPublishedVersionAdvances() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill skill = new Skill(7L, "smart-agent", "owner-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Smart Agent");
        skill.setSummary("Builds workflows");
        skill.setLatestVersionId(100L);

        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");

        SkillVersion latestVersion = new SkillVersion(1L, "1.3.0", "owner-1");
        latestVersion.setParsedMetadataJson("""
                {
                  "name": "Smart Agent",
                  "description": "Builds workflows",
                  "version": "1.3.0",
                  "frontmatter": {
                    "tags": ["中文搜索"],
                    "keywords": ["智能体"],
                    "maintainer": "新版维护者"
                  }
                }
                """);

        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(100L)).thenReturn(Optional.of(latestVersion));

        PostgresSearchRebuildService service = new PostgresSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                searchIndexService,
                new SearchTextTokenizer()
        );

        service.rebuildBySkill(1L);

        ArgumentCaptor<SkillSearchDocument> captor = ArgumentCaptor.forClass(SkillSearchDocument.class);
        verify(searchIndexService).index(captor.capture());

        SkillSearchDocument document = captor.getValue();
        assertThat(document.keywords()).contains("中文搜索");
        assertThat(document.keywords()).contains("中文");
        assertThat(document.keywords()).contains("搜索");
        assertThat(document.keywords()).contains("智能体");
        assertThat(document.searchText()).contains("smart-agent");
        assertThat(document.searchText()).contains("Builds workflows");
        assertThat(document.searchText()).contains("maintainer");
        assertThat(document.searchText()).contains("新版维护者");
        assertThat(document.searchText()).doesNotContain("中文搜索");
        assertThat(document.searchText()).doesNotContain("智能体");
    }

    @Test
    void rebuildBySkill_shouldIgnoreNullFrontmatterValues() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill skill = new Skill(7L, "smart-agent", "owner-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Smart Agent");
        skill.setSummary("Builds workflows");
        skill.setLatestVersionId(101L);

        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");

        SkillVersion version = new SkillVersion(1L, "1.4.0", "owner-1");
        version.setParsedMetadataJson("""
                {
                  "name": "Smart Agent",
                  "description": "Builds workflows",
                  "version": "1.4.0",
                  "frontmatter": {
                    "tags": ["automation"],
                    "maintainer": null,
                    "config": {
                      "provider": "openai",
                      "region": null
                    }
                  }
                }
                """);

        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(101L)).thenReturn(Optional.of(version));

        PostgresSearchRebuildService service = new PostgresSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                searchIndexService,
                new SearchTextTokenizer()
        );

        service.rebuildBySkill(1L);

        ArgumentCaptor<SkillSearchDocument> captor = ArgumentCaptor.forClass(SkillSearchDocument.class);
        verify(searchIndexService).index(captor.capture());

        SkillSearchDocument document = captor.getValue();
        assertThat(document.keywords()).contains("automation");
        assertThat(document.searchText()).contains("smart-agent");
        assertThat(document.searchText()).contains("Builds workflows");
        assertThat(document.searchText()).contains("config");
        assertThat(document.searchText()).contains("provider");
        assertThat(document.searchText()).contains("openai");
        assertThat(document.searchText()).doesNotContain("maintainer");
        assertThat(document.searchText()).doesNotContain("automation");
        assertThat(document.searchText()).doesNotContain("region null");
    }

    @Test
    void rebuildBySkill_shouldKeepChineseSummarySearchableThroughSearchText() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill skill = new Skill(7L, "smart-agent", "owner-1", SkillVisibility.PUBLIC);
        skill.setDisplayName("Smart Agent");
        skill.setSummary("支持中文描述搜索");
        skill.setLatestVersionId(102L);

        Namespace namespace = new Namespace("team-ai", "Team AI", "owner-1");
        SkillVersion version = new SkillVersion(1L, "1.5.0", "owner-1");
        version.setParsedMetadataJson("""
                {
                  "name": "Smart Agent",
                  "description": "支持中文描述搜索",
                  "version": "1.5.0",
                  "frontmatter": {
                    "author": "Jane Doe"
                  }
                }
                """);

        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(skillVersionRepository.findById(102L)).thenReturn(Optional.of(version));

        PostgresSearchRebuildService service = new PostgresSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                searchIndexService,
                new SearchTextTokenizer()
        );

        service.rebuildBySkill(1L);

        ArgumentCaptor<SkillSearchDocument> captor = ArgumentCaptor.forClass(SkillSearchDocument.class);
        verify(searchIndexService).index(captor.capture());

        SkillSearchDocument document = captor.getValue();
        assertThat(document.searchText()).contains("支持中文描述搜索");
        assertThat(document.searchText()).contains("中文");
        assertThat(document.searchText()).contains("描述");
        assertThat(document.searchText()).contains("搜索");
    }
}
