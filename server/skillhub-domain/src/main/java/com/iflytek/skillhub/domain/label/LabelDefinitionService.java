package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelDefinitionService {

    private final int maxLabelDefinitions;

    private final LabelDefinitionRepository labelDefinitionRepository;
    private final LabelTranslationRepository labelTranslationRepository;
    private final LabelPermissionChecker labelPermissionChecker;

    public LabelDefinitionService(LabelDefinitionRepository labelDefinitionRepository,
                                  LabelTranslationRepository labelTranslationRepository,
                                  LabelPermissionChecker labelPermissionChecker,
                                  @Value("${skillhub.label.max-definitions:100}") int maxLabelDefinitions) {
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.labelTranslationRepository = labelTranslationRepository;
        this.labelPermissionChecker = labelPermissionChecker;
        this.maxLabelDefinitions = maxLabelDefinitions;
    }

    public List<LabelDefinition> listAll() {
        return labelDefinitionRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public List<LabelDefinition> listVisibleFilters() {
        return labelDefinitionRepository.findByVisibleInFilterTrueOrderBySortOrderAscIdAsc();
    }

    public List<LabelDefinition> listByIds(List<Long> labelIds) {
        if (labelIds == null || labelIds.isEmpty()) {
            return List.of();
        }
        return labelDefinitionRepository.findByIdIn(labelIds);
    }

    public LabelDefinition getBySlug(String slug) {
        String normalizedSlug = LabelSlugValidator.normalize(slug);
        return labelDefinitionRepository.findBySlugIgnoreCase(normalizedSlug)
                .orElseThrow(() -> new DomainBadRequestException("label.not_found", normalizedSlug));
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
        String normalizedSlug = LabelSlugValidator.normalize(slug);
        List<LabelTranslation> normalizedTranslations = normalizeTranslations(translations);
        if (labelDefinitionRepository.count() >= maxLabelDefinitions) {
            throw new DomainBadRequestException("label.definition.too_many", maxLabelDefinitions);
        }
        if (labelDefinitionRepository.findBySlugIgnoreCase(normalizedSlug).isPresent()) {
            throw new DomainBadRequestException("label.slug.duplicate", normalizedSlug);
        }
        try {
            LabelDefinition labelDefinition = labelDefinitionRepository.save(
                    new LabelDefinition(normalizedSlug, type, visibleInFilter, sortOrder, operatorId)
            );
            replaceTranslations(labelDefinition.getId(), normalizedTranslations);
            return labelDefinition;
        } catch (DataIntegrityViolationException ex) {
            throw mapConstraintViolation(normalizedSlug, ex);
        }
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
        List<LabelTranslation> normalizedTranslations = normalizeTranslations(translations);
        existing.setType(type);
        existing.setVisibleInFilter(visibleInFilter);
        existing.setSortOrder(sortOrder);
        try {
            LabelDefinition saved = labelDefinitionRepository.save(existing);
            replaceTranslations(saved.getId(), normalizedTranslations);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw mapConstraintViolation(existing.getSlug(), ex);
        }
    }

    @Transactional
    public void delete(String slug, Set<String> platformRoles) {
        requireDefinitionAdmin(platformRoles);
        labelDefinitionRepository.delete(getBySlug(slug));
    }

    @Transactional
    public List<LabelDefinition> updateSortOrders(List<LabelSortOrderUpdate> updates, Set<String> platformRoles) {
        requireDefinitionAdmin(platformRoles);
        if (updates == null || updates.isEmpty()) {
            throw new DomainBadRequestException("label.sort_order.empty");
        }
        List<LabelDefinition> labels = labelDefinitionRepository.findByIdIn(
                updates.stream().map(LabelSortOrderUpdate::labelId).toList()
        );
        if (labels.size() != updates.size()) {
            throw new DomainBadRequestException("label.not_found");
        }
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

    public Map<Long, List<LabelTranslation>> listTranslationsByLabelIds(List<Long> labelIds) {
        if (labelIds == null || labelIds.isEmpty()) {
            return Map.of();
        }
        return labelTranslationRepository.findByLabelIdIn(labelIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(LabelTranslation::getLabelId));
    }

    private void replaceTranslations(Long labelId, List<LabelTranslation> translations) {
        List<LabelTranslation> existingTranslations = labelTranslationRepository.findByLabelId(labelId);
        if (!existingTranslations.isEmpty()) {
            labelTranslationRepository.deleteAll(existingTranslations);
            labelTranslationRepository.flush();
        }
        if (!translations.isEmpty()) {
            labelTranslationRepository.saveAll(translations.stream()
                    .map(translation -> new LabelTranslation(labelId, translation.getLocale(), translation.getDisplayName()))
                    .toList());
        }
    }

    private List<LabelTranslation> normalizeTranslations(List<LabelTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            throw new DomainBadRequestException("label.translation.empty");
        }
        List<LabelTranslation> normalized = translations.stream()
                .map(translation -> new LabelTranslation(
                        null,
                        normalizeLocale(translation.getLocale()),
                        normalizeDisplayName(translation.getDisplayName())))
                .toList();
        Map<String, Long> counts = normalized.stream()
                .map(LabelTranslation::getLocale)
                .collect(java.util.stream.Collectors.groupingBy(Function.identity(), java.util.stream.Collectors.counting()));
        String duplicateLocale = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (duplicateLocale != null) {
            throw new DomainBadRequestException("label.translation.locale.duplicate", duplicateLocale);
        }
        return normalized;
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            throw new DomainBadRequestException("label.translation.locale.blank");
        }
        return locale.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new DomainBadRequestException("label.translation.display_name.blank");
        }
        return displayName.trim();
    }

    private void requireDefinitionAdmin(Set<String> platformRoles) {
        if (!labelPermissionChecker.canManageDefinitions(platformRoles)) {
            throw new DomainForbiddenException("label.definition.no_permission");
        }
    }

    private DomainBadRequestException mapConstraintViolation(String slug, DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message != null && message.contains("label_translation")) {
            return new DomainBadRequestException("label.translation.locale.conflict");
        }
        return new DomainBadRequestException("label.slug.duplicate", slug);
    }

    public record LabelSortOrderUpdate(Long labelId, int sortOrder) {
    }
}
