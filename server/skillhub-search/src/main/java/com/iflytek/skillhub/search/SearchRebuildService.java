package com.iflytek.skillhub.search;

/**
 * Rebuilds search index state from authoritative domain data.
 */
public interface SearchRebuildService {
    void rebuildAll();
    void rebuildByNamespace(Long namespaceId);
    void rebuildBySkill(Long skillId);
}
