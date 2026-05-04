package com.iflytek.skillhub.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import org.junit.jupiter.api.Test;

class JpaSearchRebuildServiceTest {

    @Test
    void constructorWiresDependencies() {
        SkillRepository skillRepository = mock(SkillRepository.class);
        NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
        SkillVersionRepository skillVersionRepository = mock(SkillVersionRepository.class);
        LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
        LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
        SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);
        SearchIndexService searchIndexService = mock(SearchIndexService.class);
        SearchTextTokenizer searchTextTokenizer = mock(SearchTextTokenizer.class);

        JpaSearchRebuildService service = new JpaSearchRebuildService(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                searchIndexService,
                searchTextTokenizer
        );

        assertThat(service).isNotNull();
    }
}
