package com.iflytek.skillhub.search.event;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillStatusChangedEvent;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.search.SearchIndexService;
import com.iflytek.skillhub.search.SearchRebuildService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to committed skill lifecycle events and keeps the search index synchronized.
 */
@Component
public class SearchIndexEventListener {

    private final SearchRebuildService searchRebuildService;
    private final SearchIndexService searchIndexService;

    public SearchIndexEventListener(
            SearchRebuildService searchRebuildService,
            SearchIndexService searchIndexService) {
        this.searchRebuildService = searchRebuildService;
        this.searchIndexService = searchIndexService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    public void onSkillPublished(SkillPublishedEvent event) {
        searchRebuildService.rebuildBySkill(event.skillId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("skillhubEventExecutor")
    public void onSkillStatusChanged(SkillStatusChangedEvent event) {
        if (event.newStatus() == SkillStatus.ARCHIVED) {
            searchIndexService.remove(event.skillId());
        } else {
            searchRebuildService.rebuildBySkill(event.skillId());
        }
    }
}
