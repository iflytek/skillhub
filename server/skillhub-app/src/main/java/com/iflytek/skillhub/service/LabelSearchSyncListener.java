package com.iflytek.skillhub.service;

import com.iflytek.skillhub.search.SearchRebuildService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LabelSearchSyncListener {

    private static final Logger log = LoggerFactory.getLogger(LabelSearchSyncListener.class);

    private final SearchRebuildService searchRebuildService;

    public LabelSearchSyncListener(SearchRebuildService searchRebuildService) {
        this.searchRebuildService = searchRebuildService;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLabelSearchSyncRequested(LabelSearchSyncRequestedEvent event) {
        List<Long> skillIds = event.skillIds() == null ? List.of() : event.skillIds();
        for (Long skillId : skillIds) {
            try {
                searchRebuildService.rebuildBySkill(skillId);
            } catch (Exception ex) {
                log.error("Failed to rebuild search document for skill {}", skillId, ex);
            }
        }
    }
}
