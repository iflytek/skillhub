package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.skill.SkillRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SkillLabelServiceTest {

    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
    private final SkillLabelRepository skillLabelRepository = mock(SkillLabelRepository.class);
    private final LabelPermissionChecker labelPermissionChecker = mock(LabelPermissionChecker.class);

    @Test
    void constructorShouldRejectNonPositivePerSkillLimit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new SkillLabelService(
                skillRepository,
                labelDefinitionRepository,
                skillLabelRepository,
                labelPermissionChecker,
                0
        ));

        assertEquals("skillhub.label.max-per-skill must be greater than 0", ex.getMessage());
    }
}
