package com.iflytek.skillhub.service;

import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.search.SearchRebuildService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class LabelSearchSyncServiceTest {

    @Test
    void rebuildSkillsShouldSkipNullsAndDuplicatesWhileProcessingLargeLists() {
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LabelSearchSyncService service = new LabelSearchSyncService(
            rebuildService,
            new SkillHubMetrics(meterRegistry)
        );
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
        assertThat(meterRegistry.find("skillhub.search.rebuild.failure").counter()).isNull();
    }

    @Test
    void rebuildSkillShouldRetryTransientFailureWithoutRecordingFailureMetric() {
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LabelSearchSyncService service = new LabelSearchSyncService(
            rebuildService,
            new SkillHubMetrics(meterRegistry)
        );
        doThrow(new IllegalStateException("temporary index failure"))
            .doNothing()
            .when(rebuildService)
            .rebuildBySkill(42L);

        service.rebuildSkill(42L);

        verify(rebuildService, times(2)).rebuildBySkill(42L);
        assertThat(meterRegistry.find("skillhub.search.rebuild.failure").counter()).isNull();
    }

    @Test
    void rebuildSkillShouldRecordFailureMetricAfterRetriesAreExhausted() {
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LabelSearchSyncService service = new LabelSearchSyncService(
            rebuildService,
            new SkillHubMetrics(meterRegistry)
        );
        doThrow(new IllegalStateException("index unavailable"))
            .when(rebuildService)
            .rebuildBySkill(42L);

        service.rebuildSkill(42L);

        verify(rebuildService, times(LabelSearchSyncService.REBUILD_MAX_ATTEMPTS)).rebuildBySkill(42L);
        assertThat(meterRegistry.get("skillhub.search.rebuild.failure")
            .tag("trigger", "single")
            .counter()
            .count()).isEqualTo(1.0d);
    }

    @Test
    void rebuildSkillsShouldRecordBatchFailureMetricAndContinue() {
        SearchRebuildService rebuildService = mock(SearchRebuildService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        LabelSearchSyncService service = new LabelSearchSyncService(
            rebuildService,
            new SkillHubMetrics(meterRegistry)
        );
        doThrow(new IllegalStateException("temporary index failure"))
            .when(rebuildService)
            .rebuildBySkill(2L);

        service.rebuildSkills(List.of(1L, 2L, 3L));

        verify(rebuildService).rebuildBySkill(1L);
        verify(rebuildService, times(LabelSearchSyncService.REBUILD_MAX_ATTEMPTS)).rebuildBySkill(2L);
        verify(rebuildService).rebuildBySkill(3L);
        assertThat(meterRegistry.get("skillhub.search.rebuild.failure")
            .tag("trigger", "batch")
            .counter()
            .count()).isEqualTo(1.0d);
    }
}
