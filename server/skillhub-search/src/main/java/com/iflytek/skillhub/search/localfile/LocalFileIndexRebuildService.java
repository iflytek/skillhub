package com.iflytek.skillhub.search.localfile;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.search.AbstractJpaSearchRebuildService;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchTextTokenizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rebuilds Lucene documents from canonical data for the local-file-index provider.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.search", name = "provider", havingValue = "local-file-index")
public class LocalFileIndexRebuildService extends AbstractJpaSearchRebuildService {

    public LocalFileIndexRebuildService(
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
