package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabelDefinitionServiceTest {

    private final LabelDefinitionRepository labelDefinitionRepository = mock(LabelDefinitionRepository.class);
    private final LabelTranslationRepository labelTranslationRepository = mock(LabelTranslationRepository.class);
    private final LabelPermissionChecker labelPermissionChecker = mock(LabelPermissionChecker.class);
    private final LabelDefinitionService service = new LabelDefinitionService(
            labelDefinitionRepository,
            labelTranslationRepository,
            labelPermissionChecker,
            100
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

    @Test
    void updateShouldFlushDeletedTranslationsBeforeSavingReplacements() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        LabelDefinition definition = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(definition, "id", 10L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(definition));
        when(labelDefinitionRepository.save(definition)).thenReturn(definition);
        when(labelTranslationRepository.findByLabelId(10L)).thenReturn(List.of(
                new LabelTranslation(10L, "en", "Official")
        ));

        service.update(
                "official",
                LabelType.RECOMMENDED,
                true,
                1,
                List.of(new LabelTranslation(null, "en", "Official Updated")),
                Set.of("SUPER_ADMIN")
        );

        var inOrder = inOrder(labelTranslationRepository);
        inOrder.verify(labelTranslationRepository).deleteAll(org.mockito.ArgumentMatchers.any());
        inOrder.verify(labelTranslationRepository).flush();
        inOrder.verify(labelTranslationRepository).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listVisibleFiltersShouldIncludePrivilegedLabelsWhenVisible() {
        List<LabelDefinition> expected = List.of(
                new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin"),
                new LabelDefinition("verified", LabelType.PRIVILEGED, true, 1, "admin")
        );
        when(labelDefinitionRepository.findByVisibleInFilterTrueOrderBySortOrderAscIdAsc()).thenReturn(expected);

        List<LabelDefinition> actual = service.listVisibleFilters();

        assertEquals(expected, actual);
        verify(labelDefinitionRepository).findByVisibleInFilterTrueOrderBySortOrderAscIdAsc();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
