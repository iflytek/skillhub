package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import com.iflytek.skillhub.projection.SkillEngagementProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRatingEventListenerTest {
    @Mock SkillEngagementProjectionService skillEngagementProjectionService;
    @InjectMocks SkillRatingEventListener listener;

    @Test
    void onRated_updates_rating_avg_and_count() {
        listener.onRated(new SkillRatedEvent(1L, "10", (short) 5));
        verify(skillEngagementProjectionService).refreshRatingStats(1L);
    }
}
