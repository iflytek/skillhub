package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import com.iflytek.skillhub.projection.SkillEngagementProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillStarEventListenerTest {
    @Mock SkillEngagementProjectionService skillEngagementProjectionService;
    @InjectMocks SkillStarEventListener listener;

    @Test
    void onStarred_updates_star_count() {
        listener.onStarred(new SkillStarredEvent(1L, "10"));
        verify(skillEngagementProjectionService).refreshStarCount(1L);
    }

    @Test
    void onUnstarred_updates_star_count() {
        listener.onUnstarred(new SkillUnstarredEvent(1L, "10"));
        verify(skillEngagementProjectionService).refreshStarCount(1L);
    }
}
