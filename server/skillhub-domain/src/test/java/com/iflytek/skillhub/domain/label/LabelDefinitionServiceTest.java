package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void constructorShouldRejectNonPositiveDefinitionLimit() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new LabelDefinitionService(
                labelDefinitionRepository,
                labelTranslationRepository,
                labelPermissionChecker,
                0
        ));

        assertEquals("skillhub.label.max-definitions must be greater than 0", ex.getMessage());
    }

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

    @Test
    void listAllShouldDelegateToRepository() {
        List<LabelDefinition> expected = List.of(new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin"));
        when(labelDefinitionRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(expected);

        List<LabelDefinition> actual = service.listAll();

        assertEquals(expected, actual);
    }

    @Test
    void listByIdsShouldReturnEmptyForNull() {
        assertTrue(service.listByIds(null).isEmpty());
    }

    @Test
    void listByIdsShouldReturnEmptyForEmptyList() {
        assertTrue(service.listByIds(List.of()).isEmpty());
    }

    @Test
    void listByIdsShouldDelegateToRepository() {
        List<LabelDefinition> expected = List.of(new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin"));
        when(labelDefinitionRepository.findByIdIn(List.of(1L))).thenReturn(expected);

        assertEquals(expected, service.listByIds(List.of(1L)));
    }

    @Test
    void getBySlugShouldThrowWhenNotFound() {
        when(labelDefinitionRepository.findBySlugIgnoreCase("missing")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.getBySlug("missing"));
        assertEquals("label.not_found", ex.messageCode());
    }

    @Test
    void createShouldSucceedWhenValid() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());
        LabelDefinition saved = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(saved, "id", 1L);
        when(labelDefinitionRepository.save(org.mockito.ArgumentMatchers.any(LabelDefinition.class))).thenReturn(saved);
        when(labelTranslationRepository.findByLabelId(1L)).thenReturn(List.of());

        LabelDefinition result = service.create("official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", "Official")), "admin", Set.of("SUPER_ADMIN"));

        assertEquals("official", result.getSlug());
        verify(labelTranslationRepository).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createShouldThrowWhenSlugAlreadyExists() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(
                new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin")));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", "Official")), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.slug.duplicate", ex.messageCode());
    }

    @Test
    void createShouldMapConstraintViolationToTranslationConflict() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());
        DataIntegrityViolationException ex = new DataIntegrityViolationException("violates unique constraint on label_translation");
        when(labelDefinitionRepository.save(org.mockito.ArgumentMatchers.any(LabelDefinition.class))).thenThrow(ex);

        DomainBadRequestException thrown = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", "Official")), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.translation.locale.conflict", thrown.messageCode());
    }

    @Test
    void createShouldMapConstraintViolationToSlugDuplicate() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());
        DataIntegrityViolationException ex = new DataIntegrityViolationException("violates unique constraint on label_definition");
        when(labelDefinitionRepository.save(org.mockito.ArgumentMatchers.any(LabelDefinition.class))).thenThrow(ex);

        DomainBadRequestException thrown = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", "Official")), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.slug.duplicate", thrown.messageCode());
    }

    @Test
    void createShouldMapConstraintViolationWithNullCause() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());
        DataIntegrityViolationException ex = new DataIntegrityViolationException("error") {
            @Override
            public Throwable getMostSpecificCause() { return null; }
        };
        when(labelDefinitionRepository.save(org.mockito.ArgumentMatchers.any(LabelDefinition.class))).thenThrow(ex);

        DomainBadRequestException thrown = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", "Official")), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.slug.duplicate", thrown.messageCode());
    }

    @Test
    void updateShouldMapConstraintViolation() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        LabelDefinition definition = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        setField(definition, "id", 10L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(definition));
        when(labelDefinitionRepository.save(definition)).thenThrow(new DataIntegrityViolationException("error"));
        when(labelTranslationRepository.findByLabelId(10L)).thenReturn(List.of());

        DomainBadRequestException thrown = assertThrows(DomainBadRequestException.class, () -> service.update(
                "official", LabelType.RECOMMENDED, true, 1,
                List.of(new LabelTranslation(null, "en", "Official")), Set.of("SUPER_ADMIN")));
        assertEquals("label.slug.duplicate", thrown.messageCode());
    }

    @Test
    void deleteShouldRemoveDefinition() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        LabelDefinition definition = new LabelDefinition("official", LabelType.RECOMMENDED, true, 0, "admin");
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.of(definition));

        service.delete("official", Set.of("SUPER_ADMIN"));

        verify(labelDefinitionRepository).delete(definition);
    }

    @Test
    void updateSortOrdersShouldRejectNullUpdates() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.updateSortOrders(null, Set.of("SUPER_ADMIN")));
        assertEquals("label.sort_order.empty", ex.messageCode());
    }

    @Test
    void updateSortOrdersShouldRejectEmptyUpdates() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.updateSortOrders(List.of(), Set.of("SUPER_ADMIN")));
        assertEquals("label.sort_order.empty", ex.messageCode());
    }

    @Test
    void updateSortOrdersShouldMatchLabelsToUpdates() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        LabelDefinition label1 = new LabelDefinition("a", LabelType.RECOMMENDED, true, 0, "admin");
        setField(label1, "id", 1L);
        LabelDefinition label2 = new LabelDefinition("b", LabelType.RECOMMENDED, true, 1, "admin");
        setField(label2, "id", 2L);
        when(labelDefinitionRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(label1, label2));
        when(labelDefinitionRepository.saveAll(List.of(label1, label2))).thenReturn(List.of(label1, label2));

        List<LabelDefinition> result = service.updateSortOrders(List.of(
                new LabelDefinitionService.LabelSortOrderUpdate(1L, 5),
                new LabelDefinitionService.LabelSortOrderUpdate(2L, 10)
        ), Set.of("SUPER_ADMIN"));

        assertEquals(5, label1.getSortOrder());
        assertEquals(10, label2.getSortOrder());
    }

    @Test
    void listTranslationsShouldDelegateToRepository() {
        List<LabelTranslation> expected = List.of(new LabelTranslation(1L, "en", "Official"));
        when(labelTranslationRepository.findByLabelId(1L)).thenReturn(expected);

        assertEquals(expected, service.listTranslations(1L));
    }

    @Test
    void listTranslationsByLabelIdsShouldReturnEmptyForNull() {
        assertTrue(service.listTranslationsByLabelIds(null).isEmpty());
    }

    @Test
    void listTranslationsByLabelIdsShouldReturnEmptyForEmptyList() {
        assertTrue(service.listTranslationsByLabelIds(List.of()).isEmpty());
    }

    @Test
    void listTranslationsByLabelIdsShouldGroupByLabelId() {
        List<LabelTranslation> translations = List.of(
                new LabelTranslation(1L, "en", "A"),
                new LabelTranslation(1L, "zh", "B"),
                new LabelTranslation(2L, "en", "C")
        );
        when(labelTranslationRepository.findByLabelIdIn(List.of(1L, 2L))).thenReturn(translations);

        Map<Long, List<LabelTranslation>> result = service.listTranslationsByLabelIds(List.of(1L, 2L));
        assertEquals(2, result.size());
        assertEquals(2, result.get(1L).size());
        assertEquals(1, result.get(2L).size());
    }

    @Test
    void normalizeTranslationsShouldRejectNull() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0, null, "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.translation.empty", ex.messageCode());
    }

    @Test
    void normalizeTranslationsShouldRejectEmpty() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0, List.of(), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.translation.empty", ex.messageCode());
    }

    @Test
    void normalizeLocaleShouldRejectNull() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, null, "Official")), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.translation.locale.blank", ex.messageCode());
    }

    @Test
    void normalizeLocaleShouldRejectBlank() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "   ", "Official")), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.translation.locale.blank", ex.messageCode());
    }

    @Test
    void normalizeDisplayNameShouldRejectNull() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", null)), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.translation.display_name.blank", ex.messageCode());
    }

    @Test
    void normalizeDisplayNameShouldRejectBlank() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("SUPER_ADMIN"))).thenReturn(true);
        when(labelDefinitionRepository.count()).thenReturn(0L);
        when(labelDefinitionRepository.findBySlugIgnoreCase("official")).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", "   ")), "admin", Set.of("SUPER_ADMIN")));
        assertEquals("label.translation.display_name.blank", ex.messageCode());
    }

    @Test
    void requireDefinitionAdminShouldThrowWhenNoPermission() {
        when(labelPermissionChecker.canManageDefinitions(Set.of("USER"))).thenReturn(false);

        DomainForbiddenException ex = assertThrows(DomainForbiddenException.class, () -> service.create(
                "official", LabelType.RECOMMENDED, true, 0,
                List.of(new LabelTranslation(null, "en", "Official")), "admin", Set.of("USER")));
        assertEquals("label.definition.no_permission", ex.messageCode());
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
