package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelDefinitionService {

    private final LabelDefinitionRepository labelDefinitionRepository;
    private final LabelTranslationRepository labelTranslationRepository;
    private final LabelPermissionChecker labelPermissionChecker;

    public LabelDefinitionService(LabelDefinitionRepository labelDefinitionRepository,
                                  LabelTranslationRepository labelTranslationRepository,
                                  LabelPermissionChecker labelPermissionChecker) {
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.labelTranslationRepository = labelTranslationRepository;
        this.labelPermissionChecker = labelPermissionChecker;
    }

    public List<LabelDefinition> listAll() {
        return labelDefinitionRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public List<LabelDefinition> listVisibleFilters() {
        return labelDefinitionRepository.findByVisibleInFilterTrueAndTypeOrderBySortOrderAscIdAsc(LabelType.RECOMMENDED);
    }

    public LabelDefinition getBySlug(String slug) {
        return labelDefinitionRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("label.not_found", slug));
    }

    @Transactional
    public LabelDefinition create(String slug,
                                  LabelType type,
                                  boolean visibleInFilter,
                                  int sortOrder,
                                  List<LabelTranslation> translations,
                                  String operatorId,
                                  Set<String> platformRoles) {
        requireDefinitionAdmin(platformRoles);
        if (labelDefinitionRepository.findBySlug(slug).isPresent()) {
            throw new DomainBadRequestException("label.slug.duplicate", slug);
        }
        LabelDefinition labelDefinition = labelDefinitionRepository.save(
                new LabelDefinition(slug, type, visibleInFilter, sortOrder, operatorId)
        );
        replaceTranslations(labelDefinition.getId(), translations);
        return labelDefinition;
    }

    @Transactional
    public LabelDefinition update(String slug,
                                  LabelType type,
                                  boolean visibleInFilter,
                                  int sortOrder,
                                  List<LabelTranslation> translations,
                                  Set<String> platformRoles) {
        requireDefinitionAdmin(platformRoles);
        LabelDefinition existing = getBySlug(slug);
        existing.setType(type);
        existing.setVisibleInFilter(visibleInFilter);
        existing.setSortOrder(sortOrder);
        LabelDefinition saved = labelDefinitionRepository.save(existing);
        replaceTranslations(saved.getId(), translations);
        return saved;
    }

    @Transactional
    public void delete(String slug, Set<String> platformRoles) {
        requireDefinitionAdmin(platformRoles);
        labelDefinitionRepository.delete(getBySlug(slug));
    }

    @Transactional
    public List<LabelDefinition> updateSortOrders(List<LabelSortOrderUpdate> updates, Set<String> platformRoles) {
        requireDefinitionAdmin(platformRoles);
        List<LabelDefinition> labels = labelDefinitionRepository.findByIdIn(
                updates.stream().map(LabelSortOrderUpdate::labelId).toList()
        );
        for (LabelDefinition label : labels) {
            updates.stream()
                    .filter(update -> update.labelId().equals(label.getId()))
                    .findFirst()
                    .ifPresent(update -> label.setSortOrder(update.sortOrder()));
        }
        return labelDefinitionRepository.saveAll(labels);
    }

    public List<LabelTranslation> listTranslations(Long labelId) {
        return labelTranslationRepository.findByLabelId(labelId);
    }

    private void replaceTranslations(Long labelId, List<LabelTranslation> translations) {
        List<LabelTranslation> existingTranslations = labelTranslationRepository.findByLabelId(labelId);
        if (!existingTranslations.isEmpty()) {
            labelTranslationRepository.deleteAll(existingTranslations);
        }
        if (!translations.isEmpty()) {
            labelTranslationRepository.saveAll(translations.stream()
                    .map(translation -> new LabelTranslation(labelId, translation.getLocale(), translation.getDisplayName()))
                    .toList());
        }
    }

    private void requireDefinitionAdmin(Set<String> platformRoles) {
        if (!labelPermissionChecker.canManageDefinitions(platformRoles)) {
            throw new DomainForbiddenException("label.definition.no_permission");
        }
    }

    public record LabelSortOrderUpdate(Long labelId, int sortOrder) {
    }
}
