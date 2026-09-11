package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService.ResourceHit;
import com.iflytek.skillhub.search.ResourceDiscoveryQueryService.ResourcePage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceDiscoveryAppServiceTest {

    private final ResourceDiscoveryQueryService queryService = mock(ResourceDiscoveryQueryService.class);
    private final SkillSuiteLabelProjectionService projectionService =
            mock(SkillSuiteLabelProjectionService.class);
    private final ResourceDiscoveryAppService service =
            new ResourceDiscoveryAppService(queryService, projectionService);

    @Test
    void attachesLabelsToSuitesWithOneBatchAndNeverToSkills() {
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        ResourceHit suite = new ResourceHit(
                "SUITE", 1L, "global", "suite", "Suite", "Summary", "1.0.0",
                "PUBLIC", 1, true, now);
        ResourceHit skill = new ResourceHit(
                "SKILL", 2L, "global", "skill", "Skill", "Summary", "1.0.0",
                "PUBLIC", 2, true, now);
        when(queryService.search(anyQuery())).thenReturn(new ResourcePage(List.of(suite, skill), 2, 0, 20));
        SkillLabelDto label = new SkillLabelDto("automation", "RECOMMENDED", "Automation");
        when(projectionService.labelsBySuiteIds(List.of(1L))).thenReturn(Map.of(1L, List.of(label)));

        var response = service.search(
                null, null, "SUITE", "newest", 0, 20, Set.of(), List.of("Automation"));

        assertThat(response.items()).filteredOn(item -> item.resourceType().equals("SUITE"))
                .singleElement().extracting(item -> item.labels()).isEqualTo(List.of(label));
        assertThat(response.items()).filteredOn(item -> item.resourceType().equals("SKILL"))
                .singleElement().extracting(item -> item.labels()).isEqualTo(List.of());
        verify(projectionService).labelsBySuiteIds(List.of(1L));
        verify(queryService).search(new ResourceDiscoveryQueryService.ResourceQuery(
                null, null, "SUITE", "newest", 0, 20, Set.of(), List.of("automation")));
    }

    private ResourceDiscoveryQueryService.ResourceQuery anyQuery() {
        return org.mockito.ArgumentMatchers.any(ResourceDiscoveryQueryService.ResourceQuery.class);
    }
}
