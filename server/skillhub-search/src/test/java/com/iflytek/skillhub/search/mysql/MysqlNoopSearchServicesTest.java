package com.iflytek.skillhub.search.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.HashingSearchEmbeddingService;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SkillSearchDocument;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MysqlNoopSearchServicesTest {

    @Test
    void mysqlLikeIndexServiceShouldPersistThroughSharedJpaDocumentTable() {
        SkillSearchDocumentJpaRepository repository = mock(SkillSearchDocumentJpaRepository.class);
        when(repository.findBySkillId(42L)).thenReturn(Optional.empty());

        MysqlNoopSearchIndexService service = new MysqlNoopSearchIndexService(
                repository,
                new HashingSearchEmbeddingService()
        );

        service.index(new SkillSearchDocument(
                42L,
                7L,
                "team",
                "owner",
                "MySQL Skill",
                "summary",
                "mysql",
                "mysql searchable text",
                null,
                "PUBLIC",
                "ACTIVE",
                List.of("mysql"),
                0L,
                0D,
                1L,
                "ACTIVE",
                false
        ));

        verify(repository).save(org.mockito.ArgumentMatchers.any(SkillSearchDocumentEntity.class));
    }

    @Test
    void mysqlLikeRebuildServiceShouldIndexActiveSkill() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
        LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
        SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);

        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(42L);
        when(skill.getNamespaceId()).thenReturn(7L);
        when(skill.getSlug()).thenReturn("mysql-skill");
        when(skill.getOwnerId()).thenReturn("owner");
        when(skill.getDisplayName()).thenReturn("MySQL Skill");
        when(skill.getSummary()).thenReturn("summary");
        when(skill.getStatus()).thenReturn(SkillStatus.ACTIVE);
        when(skill.getDownloadCount()).thenReturn(0L);
        when(skill.getRatingAvg()).thenReturn(java.math.BigDecimal.ZERO);
        when(skill.isHidden()).thenReturn(false);

        Namespace namespace = mock(Namespace.class);
        when(namespace.getId()).thenReturn(7L);
        when(namespace.getSlug()).thenReturn("team");
        when(namespace.getStatus()).thenReturn(NamespaceStatus.ACTIVE);

        when(skillRepository.findById(42L)).thenReturn(Optional.of(skill));
        when(namespaceRepository.findById(7L)).thenReturn(Optional.of(namespace));
        when(skillLabelRepository.findBySkillId(42L)).thenReturn(List.of());

        MysqlNoopSearchRebuildService service = new MysqlNoopSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                searchIndexService,
                new SearchTextTokenizer()
        );

        service.rebuildBySkill(42L);

        verify(searchIndexService).index(argThat(document ->
                document.skillId().equals(42L) && document.namespaceId().equals(7L)));
    }
}
