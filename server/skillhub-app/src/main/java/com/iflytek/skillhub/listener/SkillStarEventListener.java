package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import com.iflytek.skillhub.projection.SkillEngagementProjectionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the stored star count in sync with the star/unstar event stream.
 */
@Component
public class SkillStarEventListener {
    private final SkillEngagementProjectionService skillEngagementProjectionService;

    public SkillStarEventListener(SkillEngagementProjectionService skillEngagementProjectionService) {
        this.skillEngagementProjectionService = skillEngagementProjectionService;
    }

    @Async
    @TransactionalEventListener
    public void onStarred(SkillStarredEvent event) {
        skillEngagementProjectionService.refreshStarCount(event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onUnstarred(SkillUnstarredEvent event) {
        skillEngagementProjectionService.refreshStarCount(event.skillId());
    }
}
