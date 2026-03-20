package com.iflytek.skillhub.service;

import com.iflytek.skillhub.search.SearchRebuildService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LabelSearchSyncService {

    static final int REBUILD_BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(LabelSearchSyncService.class);

    private final SearchRebuildService searchRebuildService;

    public LabelSearchSyncService(SearchRebuildService searchRebuildService) {
        this.searchRebuildService = searchRebuildService;
    }

    public void rebuildSkill(Long skillId) {
        searchRebuildService.rebuildBySkill(skillId);
    }

    @Async("skillhubEventExecutor")
    public void rebuildSkills(List<Long> skillIds) {
        List<Long> normalizedSkillIds = skillIds == null ? List.of() : skillIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        for (int i = 0; i < normalizedSkillIds.size(); i += REBUILD_BATCH_SIZE) {
            List<Long> batch = normalizedSkillIds.subList(i, Math.min(i + REBUILD_BATCH_SIZE, normalizedSkillIds.size()));
            for (Long skillId : batch) {
                try {
                    searchRebuildService.rebuildBySkill(skillId);
                } catch (RuntimeException ex) {
                    log.error("Failed to rebuild search document for skill {} after label change", skillId, ex);
                }
            }
        }
    }

    @Async("skillhubEventExecutor")
    public void rebuildSkillsAsync(List<Long> skillIds) {
        rebuildSkills(skillIds);
    }
}
