package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillPublishAppServiceTest {

    @Mock
    private SkillPublishService skillPublishService;

    @Mock
    private SkillLabelAppService skillLabelAppService;

    @Test
    void publishFromEntries_shouldAttachLabelsInSingleBatchAfterPublish() {
        SkillVersion version = new SkillVersion(12L, "1.0.0", "user-1");
        SkillPublishService.PublishResult publishResult =
                new SkillPublishService.PublishResult(12L, "demo-skill", version);
        List<PackageEntry> entries = List.of(new PackageEntry("SKILL.md", new byte[0], 0, "text/markdown"));
        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.OWNER);

        when(skillPublishService.publishFromEntries(
                "global",
                entries,
                "user-1",
                SkillVisibility.PUBLIC,
                Set.of("SUPER_ADMIN"),
                false,
                "summary"
        )).thenReturn(publishResult);

        SkillPublishAppService service = new SkillPublishAppService(skillPublishService, skillLabelAppService);

        SkillPublishAppService.PublishOutcome outcome = service.publishFromEntries(
                "global",
                entries,
                "user-1",
                SkillVisibility.PUBLIC,
                Set.of("SUPER_ADMIN"),
                false,
                "summary",
                List.of("official", "featured"),
                roles
        );

        assertEquals(publishResult, outcome.publishResult());
        assertEquals(List.of("official", "featured"), outcome.labels());
        verify(skillLabelAppService).attachLabels(
                "global",
                "demo-skill",
                List.of("official", "featured"),
                "user-1",
                roles,
                null
        );
    }

    @Test
    void publishFromEntries_shouldSkipAttachWhenLabelsAreEmpty() {
        SkillVersion version = new SkillVersion(12L, "1.0.0", "user-1");
        SkillPublishService.PublishResult publishResult =
                new SkillPublishService.PublishResult(12L, "demo-skill", version);
        List<PackageEntry> entries = List.of(new PackageEntry("SKILL.md", new byte[0], 0, "text/markdown"));

        when(skillPublishService.publishFromEntries(
                "global",
                entries,
                "user-1",
                SkillVisibility.PUBLIC,
                Set.of("SUPER_ADMIN"),
                false,
                null
        )).thenReturn(publishResult);

        SkillPublishAppService service = new SkillPublishAppService(skillPublishService, skillLabelAppService);

        SkillPublishAppService.PublishOutcome outcome = service.publishFromEntries(
                "global",
                entries,
                "user-1",
                SkillVisibility.PUBLIC,
                Set.of("SUPER_ADMIN"),
                false,
                null,
                List.of(),
                Map.of()
        );

        assertEquals(List.of(), outcome.labels());
        verify(skillLabelAppService, never()).attachLabels(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }
}
