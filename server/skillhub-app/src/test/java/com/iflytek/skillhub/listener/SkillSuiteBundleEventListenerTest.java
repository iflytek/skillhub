package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillSuiteBundleAdvanceRequestedEvent;
import com.iflytek.skillhub.domain.event.SkillVersionYankedEvent;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleCoordinator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillSuiteBundleEventListenerTest {

    @Test
    void directAndLifecycleEventsWakeEachBoundOperationOnce() {
        SkillSuiteBundleMemberResultRepository repository = mock(SkillSuiteBundleMemberResultRepository.class);
        SkillSuiteBundleCoordinator coordinator = mock(SkillSuiteBundleCoordinator.class);
        SkillSuiteBundleEventListener listener = new SkillSuiteBundleEventListener(repository, coordinator);
        SkillSuiteBundleMemberResult first = mock(SkillSuiteBundleMemberResult.class);
        SkillSuiteBundleMemberResult duplicate = mock(SkillSuiteBundleMemberResult.class);
        when(first.getOperationId()).thenReturn("operation-a");
        when(duplicate.getOperationId()).thenReturn("operation-a");
        when(repository.findBySkillVersionId(9L)).thenReturn(List.of(first, duplicate));
        when(repository.findBySkillVersionId(10L)).thenReturn(List.of(first));

        listener.onAdvanceRequested(new SkillSuiteBundleAdvanceRequestedEvent("operation-direct"));
        listener.onSkillPublished(new SkillPublishedEvent(1L, 9L, "actor"));
        listener.onSkillVersionYanked(new SkillVersionYankedEvent(1L, 10L, "actor", true));

        verify(coordinator).advance("operation-direct");
        verify(coordinator, org.mockito.Mockito.times(2)).advance("operation-a");
        verify(repository).findBySkillVersionId(10L);
    }
}
