package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillSuiteLabelServiceTest {

    @Mock private SkillSuiteRepository suiteRepository;
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private SkillSuiteLabelRepository suiteLabelRepository;
    @Mock private LabelPermissionChecker labelPermissionChecker;

    private SkillSuiteLabelService service;
    private SkillSuite suite;
    private LabelDefinition label;

    @BeforeEach
    void setUp() throws Exception {
        service = new SkillSuiteLabelService(
                suiteRepository, labelDefinitionRepository, suiteLabelRepository,
                labelPermissionChecker, 2);
        suite = new SkillSuite(1L, "starter", "Starter", "author");
        label = new LabelDefinition("automation", LabelType.RECOMMENDED, true, 0, "creator");
        setId(suite, 10L);
        setId(label, 20L);
    }

    @Test
    void attachesRecommendedLabelDirectlyToSuite() {
        stubSuiteAndLabel();
        when(labelPermissionChecker.canManageSuiteLabel(
                suite, label, "author", roles(), Set.of())).thenReturn(true);
        when(suiteLabelRepository.findBySuiteIdAndLabelId(10L, 20L)).thenReturn(Optional.empty());
        when(suiteLabelRepository.findBySuiteId(10L)).thenReturn(List.of());
        when(suiteLabelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SkillSuiteLabel attached = service.attachLabel(
                10L, "Automation", "author", roles(), Set.of());

        assertThat(attached.getSuiteId()).isEqualTo(10L);
        assertThat(attached.getLabelId()).isEqualTo(20L);
        assertThat(attached.getCreatedBy()).isEqualTo("author");
    }

    @Test
    void repeatedAttachIsIdempotentEvenAtTheLimit() {
        SkillSuiteLabel existing = new SkillSuiteLabel(10L, 20L, "author");
        stubSuiteAndLabel();
        when(labelPermissionChecker.canManageSuiteLabel(
                suite, label, "author", roles(), Set.of())).thenReturn(true);
        when(suiteLabelRepository.findBySuiteIdAndLabelId(10L, 20L))
                .thenReturn(Optional.of(existing));

        assertThat(service.attachLabel(10L, "automation", "author", roles(), Set.of()))
                .isSameAs(existing);

        verify(suiteLabelRepository, never()).findBySuiteId(10L);
        verify(suiteLabelRepository, never()).save(any());
    }

    @Test
    void rejectsNewLabelAboveConfiguredLimit() {
        stubSuiteAndLabel();
        when(labelPermissionChecker.canManageSuiteLabel(
                suite, label, "author", roles(), Set.of())).thenReturn(true);
        when(suiteLabelRepository.findBySuiteIdAndLabelId(10L, 20L)).thenReturn(Optional.empty());
        when(suiteLabelRepository.findBySuiteId(10L)).thenReturn(List.of(
                new SkillSuiteLabel(10L, 21L, "author"),
                new SkillSuiteLabel(10L, 22L, "author")));

        assertThatThrownBy(() -> service.attachLabel(
                10L, "automation", "author", roles(), Set.of()))
                .isInstanceOfSatisfying(DomainBadRequestException.class,
                        exception -> assertThat(exception.messageCode())
                                .isEqualTo("label.suite.too_many"));

        verify(suiteLabelRepository, never()).save(any());
    }

    @Test
    void permissionFailureDoesNotReadOrModifyAssociations() {
        stubSuiteAndLabel();
        when(labelPermissionChecker.canManageSuiteLabel(
                suite, label, "other", roles(), Set.of())).thenReturn(false);

        assertThatThrownBy(() -> service.attachLabel(
                10L, "automation", "other", roles(), Set.of()))
                .isInstanceOf(DomainForbiddenException.class);

        verify(suiteLabelRepository, never()).findBySuiteIdAndLabelId(any(), any());
        verify(suiteLabelRepository, never()).save(any());
    }

    @Test
    void detachesOnlyTheSuiteAssociation() {
        SkillSuiteLabel existing = new SkillSuiteLabel(10L, 20L, "author");
        stubSuiteAndLabel();
        when(labelPermissionChecker.canManageSuiteLabel(
                suite, label, "author", roles(), Set.of())).thenReturn(true);
        when(suiteLabelRepository.findBySuiteIdAndLabelId(10L, 20L))
                .thenReturn(Optional.of(existing));

        service.detachLabel(10L, "automation", "author", roles(), Set.of());

        verify(suiteLabelRepository).delete(existing);
    }

    private void stubSuiteAndLabel() {
        when(suiteRepository.findById(10L)).thenReturn(Optional.of(suite));
        when(labelDefinitionRepository.findBySlugIgnoreCase("automation"))
                .thenReturn(Optional.of(label));
    }

    private Map<Long, NamespaceRole> roles() {
        return Map.of(1L, NamespaceRole.MEMBER);
    }

    private void setId(Object target, Long id) throws Exception {
        var field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
