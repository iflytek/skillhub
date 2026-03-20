package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import com.iflytek.skillhub.projection.SkillEngagementProjectionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Updates denormalized skill rating counters when rating events are emitted by
 * the social domain.
 */
@Component
public class SkillRatingEventListener {
    private final SkillEngagementProjectionService skillEngagementProjectionService;

    public SkillRatingEventListener(SkillEngagementProjectionService skillEngagementProjectionService) {
        this.skillEngagementProjectionService = skillEngagementProjectionService;
    }

    @Async
    @TransactionalEventListener
    public void onRated(SkillRatedEvent event) {
        skillEngagementProjectionService.refreshRatingStats(event.skillId());
    }
}
