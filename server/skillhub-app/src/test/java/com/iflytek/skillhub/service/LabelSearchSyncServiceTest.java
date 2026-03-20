package com.iflytek.skillhub.service;

import com.iflytek.skillhub.search.SearchRebuildService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class LabelSearchSyncServiceTest {

    @Test
    void rebuildSkillsShouldSkipNullsAndDuplicatesWhileProcessingLargeLists() {
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);
        LabelSearchSyncService service = new LabelSearchSyncService(rebuildService);
        List<Long> skillIds = new ArrayList<>();
        skillIds.add(null);
        for (long i = 1; i <= 120; i++) {
            skillIds.add(i);
        }
        skillIds.add(50L);
        skillIds.add(120L);

        service.rebuildSkills(skillIds);

        for (long i = 1; i <= 120; i++) {
            verify(rebuildService).rebuildBySkill(i);
        }
        verifyNoMoreInteractions(rebuildService);
    }
}
