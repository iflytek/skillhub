package com.iflytek.skillhub.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.iflytek.skillhub.search.SearchRebuildService;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabelSearchSyncListenerTest {

    private final SearchRebuildService searchRebuildService = mock(SearchRebuildService.class);
    private final LabelSearchSyncListener listener = new LabelSearchSyncListener(searchRebuildService);

    @Test
    void onLabelSearchSyncRequested_rebuildsAllSkills() {
        listener.onLabelSearchSyncRequested(new LabelSearchSyncRequestedEvent(List.of(1L, 2L, 3L)));

        verify(searchRebuildService).rebuildBySkill(1L);
        verify(searchRebuildService).rebuildBySkill(2L);
        verify(searchRebuildService).rebuildBySkill(3L);
    }

    @Test
    void onLabelSearchSyncRequested_handlesNullSkillIds() {
        listener.onLabelSearchSyncRequested(new LabelSearchSyncRequestedEvent(null));

        // No interactions expected
    }

    @Test
    void onLabelSearchSyncRequested_continuesOnException() {
        doThrow(new RuntimeException("boom")).when(searchRebuildService).rebuildBySkill(1L);

        listener.onLabelSearchSyncRequested(new LabelSearchSyncRequestedEvent(List.of(1L, 2L)));

        verify(searchRebuildService).rebuildBySkill(2L);
    }
}
