package com.iflytek.skillhub.search;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rebuilds denormalized search documents without tying runtime wiring to a specific database engine.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "engine", havingValue = "h2")
public class JpaSearchRebuildService extends AbstractJpaSearchRebuildService {

    public JpaSearchRebuildService(SkillRepository skillRepository,
                                   NamespaceRepository namespaceRepository,
                                   SkillVersionRepository skillVersionRepository,
                                   LabelDefinitionRepository labelDefinitionRepository,
                                   LabelTranslationRepository labelTranslationRepository,
                                   SkillLabelRepository skillLabelRepository,
                                   SearchIndexService searchIndexService,
                                   SearchTextTokenizer searchTextTokenizer) {
        super(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                labelDefinitionRepository,
                labelTranslationRepository,
                skillLabelRepository,
                searchIndexService,
                searchTextTokenizer
        );
    }
}
