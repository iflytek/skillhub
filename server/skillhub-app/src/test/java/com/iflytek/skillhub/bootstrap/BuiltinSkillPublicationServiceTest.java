package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.iflytek.skillhub.domain.label.*;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import java.util.*;
import org.junit.jupiter.api.Test;

class BuiltinSkillPublicationServiceTest {
    private final SkillPublishService publish = mock(SkillPublishService.class);
    private final SkillLabelService labels = mock(SkillLabelService.class);
    private final LabelDefinitionRepository definitions = mock(LabelDefinitionRepository.class);
    private final BuiltinSkillPublicationService service = new BuiltinSkillPublicationService(publish, labels, definitions);
    @Test void newBuiltinGetsOneOrdinaryLabel() {
        var result = new SkillPublishService.PublishResult(12L, "meeting-note-summarizer", null, true);
        when(publish.publishFromEntries(anyString(), anyList(), anyString(), any(), anySet(), anyBoolean())).thenReturn(result);
        var definition = mock(LabelDefinition.class);
        when(definition.getType()).thenReturn(LabelType.RECOMMENDED);
        when(definitions.findBySlug("productivity")).thenReturn(Optional.of(definition));
        assertThat(service.publishFromEntries("global", List.of(), "builtin-skill-publisher", SkillVisibility.PUBLIC, Set.of("SUPER_ADMIN"), true)).isSameAs(result);
        verify(labels).attachLabel(12L, "productivity", "builtin-skill-publisher", Map.of(), Set.of("SUPER_ADMIN"));
    }
    @Test void versionUpgradeNeverRestoresManuallyRemovedLabel() {
        when(publish.publishFromEntries(anyString(), anyList(), anyString(), any(), anySet(), anyBoolean()))
                .thenReturn(new SkillPublishService.PublishResult(12L, "meeting-note-summarizer", null, false));
        service.publishFromEntries("global", List.of(), "builtin-skill-publisher", SkillVisibility.PUBLIC, Set.of("SUPER_ADMIN"), true);
        verifyNoInteractions(labels, definitions);
    }
    @Test void existingPrivilegedDefinitionIsNotRepurposed() {
        when(publish.publishFromEntries(anyString(), anyList(), anyString(), any(), anySet(), anyBoolean()))
                .thenReturn(new SkillPublishService.PublishResult(12L, "meeting-note-summarizer", null, true));
        var definition = mock(LabelDefinition.class);
        when(definition.getType()).thenReturn(LabelType.PRIVILEGED);
        when(definitions.findBySlug("productivity")).thenReturn(Optional.of(definition));
        service.publishFromEntries("global", List.of(), "builtin-skill-publisher", SkillVisibility.PUBLIC, Set.of("SUPER_ADMIN"), true);
        verifyNoInteractions(labels);
    }
}
