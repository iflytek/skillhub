package com.iflytek.skillhub.search;

import java.util.List;

/**
 * Writes and removes documents in the search index implementation.
 */
public interface SearchIndexService {
    void index(SkillSearchDocument document);
    void batchIndex(List<SkillSearchDocument> documents);
    void remove(Long skillId);
}
