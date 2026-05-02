package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.search.AbstractJpaSearchRebuildService;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reconstructs PostgreSQL search documents from canonical skill and namespace records.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "engine", havingValue = "postgres", matchIfMissing = true)
public class PostgresSearchRebuildService extends AbstractJpaSearchRebuildService {

    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillVersionRepository skillVersionRepository,
            SearchIndexService searchIndexService,
            SearchTextTokenizer searchTextTokenizer) {
        super(
                skillRepository,
                namespaceRepository,
                skillVersionRepository,
                null,
                null,
                null,
                searchIndexService,
                searchTextTokenizer
        );
    }

    @Autowired
    public PostgresSearchRebuildService(
            SkillRepository skillRepository,
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
