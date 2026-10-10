package com.iflytek.skillhub.service;

import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.search.SearchRebuildService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LabelSearchSyncService {

    static final int REBUILD_BATCH_SIZE = 50;
    static final int REBUILD_MAX_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(LabelSearchSyncService.class);

    private final SearchRebuildService searchRebuildService;
    private final SkillHubMetrics metrics;

    public LabelSearchSyncService(SearchRebuildService searchRebuildService, SkillHubMetrics metrics) {
        this.searchRebuildService = searchRebuildService;
        this.metrics = metrics;
    }

    @Async("skillhubEventExecutor")
    public void rebuildSkill(Long skillId) {
        rebuildBySkill(skillId, "single", "Failed to rebuild search document for skill {}");
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
                rebuildBySkill(skillId, "batch", "Failed to rebuild search document for skill {} after label change");
            }
        }
    }

    @Async("skillhubEventExecutor")
    public void rebuildSkillsAsync(List<Long> skillIds) {
        rebuildSkills(skillIds);
    }

    private void rebuildBySkill(Long skillId, String trigger, String finalFailureMessage) {
        for (int attempt = 1; attempt <= REBUILD_MAX_ATTEMPTS; attempt++) {
            try {
                searchRebuildService.rebuildBySkill(skillId);
                return;
            } catch (RuntimeException ex) {
                if (attempt < REBUILD_MAX_ATTEMPTS) {
                    log.warn("Retrying search document rebuild for skill {} after attempt {}", skillId, attempt, ex);
                    continue;
                }
                metrics.incrementSearchRebuildFailure(trigger);
                log.error(finalFailureMessage, skillId, ex);
            }
        }
    }
}
