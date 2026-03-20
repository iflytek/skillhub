package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabelDefinitionServiceTest {

    private final LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
    private final LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
    private final LabelPermissionChecker labelPermissionChecker = mock(LabelPermissionChecker.class);
    private final LabelDefinitionService service = new LabelDefinitionService(
            labelDefinitionRepository,
            labelTranslationRepository,
            labelPermissionChecker
    );

    @Test
    void createShouldRejectDuplicateLocalesIgnoringCase() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "Official",
                LabelType.RECOMMENDED,
                true,
                0,
                List.of(
                        new LabelTranslation(null, "en", "Official"),
                        new LabelTranslation(null, "EN", "Official EN")
                ),
                "admin",
                Set.of("SUPER_ADMIN")
        ));

        assertEquals("label.translation.locale.duplicate", ex.messageCode());
    }

    @Test
    void createShouldRejectWhenDefinitionLimitReached() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(100L);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official",
                LabelType.RECOMMENDED,
                true,
                0,
                List.of(new LabelTranslation(null, "en", "Official")),
                "admin",
                Set.of("SUPER_ADMIN")
        ));

        assertEquals("label.definition.too_many", ex.messageCode());
    }

    @Test
    void getBySlugShouldIgnoreCase() {
        LabelDefinition definition = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(definition));

        LabelDefinition result = service.getBySlug("Official");

        assertEquals("official", result.getSlug());
    }

    @Test
    void updateSortOrdersShouldRejectMissingLabels() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(
                new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin")
        ));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.updateSortOrders(
                List.of(
                        new LabelDefinitionService.LabelSortOrderUpdate(1L, 0),
                        new LabelDefinitionService.LabelSortOrderUpdate(2L, 1)
                ),
                Set.of("SUPER_ADMIN")
        ));

        assertEquals("label.not_found", ex.messageCode());
    }
}
