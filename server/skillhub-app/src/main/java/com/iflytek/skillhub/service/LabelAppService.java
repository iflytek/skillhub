package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.dto.AdminLabelDefinitionResponse;
import com.iflytek.skillhub.dto.BatchLabelSortOrderRequest;
import com.iflytek.skillhub.dto.CreateLabelDefinitionRequest;
import com.iflytek.skillhub.dto.LabelResponse;
import com.iflytek.skillhub.dto.LabelTranslationRequest;
import com.iflytek.skillhub.dto.LabelTranslationResponse;
import com.iflytek.skillhub.dto.UpdateLabelDefinitionRequest;
import com.iflytek.skillhub.search.SearchRebuildService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class LabelAppService {

    private static final int MAX_LABELS_PER_SKILL = 10;

    private final LabelDefinitionRepository labelDefinitionRepository;
    private final LabelTranslationRepository labelTranslationRepository;
    private final SkillLabelRepository skillLabelRepository;
    private final NamespaceRepository namespaceRepository;
    private final VisibilityChecker visibilityChecker;
    private final SkillSlugResolutionService skillSlugResolutionService;
    private final SearchRebuildService searchRebuildService;
    private final LabelSearchSyncService labelSearchSyncService;

    public LabelAppService(LabelDefinitionRepository labelDefinitionRepository,
                           LabelTranslationRepository labelTranslationRepository,
                           SkillLabelRepository skillLabelRepository,
                           NamespaceRepository namespaceRepository,
                           VisibilityChecker visibilityChecker,
                           SkillSlugResolutionService skillSlugResolutionService,
                           SearchRebuildService searchRebuildService,
                           LabelSearchSyncService labelSearchSyncService) {
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.labelTranslationRepository = labelTranslationRepository;
        this.skillLabelRepository = skillLabelRepository;
        this.namespaceRepository = namespaceRepository;
        this.visibilityChecker = visibilityChecker;
        this.skillSlugResolutionService = skillSlugResolutionService;
        this.searchRebuildService = searchRebuildService;
        this.labelSearchSyncService = labelSearchSyncService;
    }

    @Transactional(readOnly = true)
    public List<AdminLabelDefinitionResponse> listDefinitions() {
        List<LabelDefinition> definitions = labelDefinitionRepository.findAllByOrderBySortOrderAscSlugAsc();
        Map<Long, List<LabelTranslation>> translationsByLabelId =
                groupTranslations(labelTranslationRepository.findByLabelIdIn(definitions.stream().map(LabelDefinition::getId).toList()));
        return definitions.stream()
                .map(definition -> new AdminLabelDefinitionResponse(
                        definition.getSlug(),
                        definition.getType(),
                        definition.isVisibleInFilter(),
                        definition.getSortOrder(),
                        translationsByLabelId.getOrDefault(definition.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(LabelTranslation::getLocale))
                                .map(translation -> new LabelTranslationResponse(translation.getLocale(), translation.getDisplayName()))
                                .toList(),
                        definition.getCreatedAt()))
                .toList();
    }

    @Transactional
    public AdminLabelDefinitionResponse createDefinition(CreateLabelDefinitionRequest request, String createdBy) {
        String normalizedSlug = normalizeSlug(request.slug());
        labelDefinitionRepository.findBySlug(normalizedSlug).ifPresent(existing -> {
            throw new DomainBadRequestException("label.slug.exists", normalizedSlug);
        });
        validateTranslations(request.translations());
        LabelDefinition definition = labelDefinitionRepository.save(new LabelDefinition(
                normalizedSlug,
                request.type(),
                request.visibleInFilter(),
                request.sortOrder(),
                createdBy));
        replaceTranslations(definition.getId(), request.translations());
        return toAdminResponse(definition, labelTranslationRepository.findByLabelId(definition.getId()));
    }

    @Transactional
    public AdminLabelDefinitionResponse updateDefinition(String slug, UpdateLabelDefinitionRequest request) {
        LabelDefinition definition = getDefinitionBySlug(slug);
        validateTranslations(request.translations());
        List<Long> affectedSkillIds = snapshotSkillIdsForLabel(definition.getId());
        definition.setType(request.type());
        definition.setVisibleInFilter(request.visibleInFilter());
        definition.setSortOrder(request.sortOrder());
        LabelDefinition saved = labelDefinitionRepository.save(definition);
        replaceTranslations(saved.getId(), request.translations());
        afterCommit(() -> labelSearchSyncService.rebuildSkills(affectedSkillIds));
        return toAdminResponse(saved, labelTranslationRepository.findByLabelId(saved.getId()));
    }

    @Transactional
    public void deleteDefinition(String slug) {
        LabelDefinition definition = getDefinitionBySlug(slug);
        List<Long> affectedSkillIds = snapshotSkillIdsForLabel(definition.getId());
        labelDefinitionRepository.delete(definition);
        afterCommit(() -> labelSearchSyncService.rebuildSkills(affectedSkillIds));
    }

    @Transactional
    public void updateSortOrder(BatchLabelSortOrderRequest request) {
        for (var item : request.items()) {
            LabelDefinition definition = getDefinitionBySlug(item.slug());
            definition.setSortOrder(item.sortOrder());
            labelDefinitionRepository.save(definition);
        }
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> listVisibleLabels() {
        return toLocalizedResponses(labelDefinitionRepository.findByVisibleInFilterTrueOrderBySortOrderAscSlugAsc());
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> listSkillLabels(String namespaceSlug,
                                               String skillSlug,
                                               String currentUserId,
                                               Map<Long, NamespaceRole> userNsRoles) {
        Skill skill = resolveVisibleSkill(namespaceSlug, skillSlug, currentUserId, userNsRoles);
        return listSkillLabelsBySkillId(skill.getId());
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> listSkillLabelsBySkillId(Long skillId) {
        List<SkillLabel> assignments = skillLabelRepository.findBySkillId(skillId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<LabelDefinition> definitions = labelDefinitionRepository.findByIdIn(assignments.stream()
                .map(SkillLabel::getLabelId)
                .distinct()
                .toList());
        return toLocalizedResponses(definitions);
    }

    @Transactional
    public List<LabelResponse> assignLabel(String namespaceSlug,
                                           String skillSlug,
                                           String labelSlug,
                                           String currentUserId,
                                           Map<Long, NamespaceRole> userNsRoles,
                                           Set<String> platformRoles) {
        Skill skill = resolveVisibleSkill(namespaceSlug, skillSlug, currentUserId, userNsRoles);
        LabelDefinition definition = getDefinitionBySlug(labelSlug);
        assertCanMutateLabel(definition, skill, currentUserId, userNsRoles, platformRoles);
        if (skillLabelRepository.findBySkillIdAndLabelId(skill.getId(), definition.getId()).isEmpty()) {
            if (skillLabelRepository.countBySkillId(skill.getId()) >= MAX_LABELS_PER_SKILL) {
                throw new DomainBadRequestException("label.skill.limit_exceeded", skill.getSlug());
            }
            skillLabelRepository.save(new SkillLabel(skill.getId(), definition.getId(), currentUserId));
            afterCommit(() -> searchRebuildService.rebuildBySkill(skill.getId()));
        }
        return listSkillLabelsBySkillId(skill.getId());
    }

    @Transactional
    public void removeLabel(String namespaceSlug,
                            String skillSlug,
                            String labelSlug,
                            String currentUserId,
                            Map<Long, NamespaceRole> userNsRoles,
                            Set<String> platformRoles) {
        Skill skill = resolveVisibleSkill(namespaceSlug, skillSlug, currentUserId, userNsRoles);
        LabelDefinition definition = getDefinitionBySlug(labelSlug);
        assertCanMutateLabel(definition, skill, currentUserId, userNsRoles, platformRoles);
        skillLabelRepository.findBySkillIdAndLabelId(skill.getId(), definition.getId())
                .ifPresent(existing -> {
                    skillLabelRepository.delete(existing);
                    afterCommit(() -> searchRebuildService.rebuildBySkill(skill.getId()));
                });
    }

    private AdminLabelDefinitionResponse toAdminResponse(LabelDefinition definition, List<LabelTranslation> translations) {
        return new AdminLabelDefinitionResponse(
                definition.getSlug(),
                definition.getType(),
                definition.isVisibleInFilter(),
                definition.getSortOrder(),
                translations.stream()
                        .sorted(Comparator.comparing(LabelTranslation::getLocale))
                        .map(translation -> new LabelTranslationResponse(translation.getLocale(), translation.getDisplayName()))
                        .toList(),
                definition.getCreatedAt());
    }

    private void replaceTranslations(Long labelId, List<LabelTranslationRequest> translations) {
        labelTranslationRepository.deleteByLabelId(labelId);
        labelTranslationRepository.saveAll(translations.stream()
                .map(translation -> new LabelTranslation(labelId, normalizeLocale(translation.locale()), translation.displayName().trim()))
                .toList());
    }

    private void validateTranslations(List<LabelTranslationRequest> translations) {
        Map<String, Boolean> locales = new HashMap<>();
        for (LabelTranslationRequest translation : translations) {
            String locale = normalizeLocale(translation.locale());
            if (locales.put(locale, Boolean.TRUE) != null) {
                throw new DomainBadRequestException("label.translation.duplicate_locale", locale);
            }
        }
    }

    private Skill resolveVisibleSkill(String namespaceSlug,
                                      String skillSlug,
                                      String currentUserId,
                                      Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespaceSlug));
        Skill skill = skillSlugResolutionService.resolve(
                namespace.getId(),
                skillSlug,
                currentUserId,
                SkillSlugResolutionService.Preference.CURRENT_USER);
        if (!visibilityChecker.canAccess(skill, currentUserId, userNsRoles != null ? userNsRoles : Map.of())) {
            throw new DomainForbiddenException("error.skill.access.denied", skillSlug);
        }
        return skill;
    }

    private List<LabelResponse> toLocalizedResponses(List<LabelDefinition> definitions) {
        if (definitions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LabelTranslation>> translationsByLabelId =
                groupTranslations(labelTranslationRepository.findByLabelIdIn(definitions.stream().map(LabelDefinition::getId).toList()));
        Locale locale = LocaleContextHolder.getLocale();
        return definitions.stream()
                .sorted(Comparator.comparingInt(LabelDefinition::getSortOrder).thenComparing(LabelDefinition::getSlug))
                .map(definition -> new LabelResponse(
                        definition.getSlug(),
                        definition.getType(),
                        resolveDisplayName(definition, translationsByLabelId.getOrDefault(definition.getId(), List.of()), locale)))
                .toList();
    }

    private String resolveDisplayName(LabelDefinition definition, List<LabelTranslation> translations, Locale locale) {
        if (translations.isEmpty()) {
            return definition.getSlug();
        }
        Map<String, String> translationsByLocale = new LinkedHashMap<>();
        for (LabelTranslation translation : translations) {
            translationsByLocale.put(normalizeLocale(translation.getLocale()), translation.getDisplayName());
        }
        String exact = normalizeLocale(locale.toLanguageTag());
        if (translationsByLocale.containsKey(exact)) {
            return translationsByLocale.get(exact);
        }
        String language = normalizeLocale(locale.getLanguage());
        if (!language.isBlank() && translationsByLocale.containsKey(language)) {
            return translationsByLocale.get(language);
        }
        if (translationsByLocale.containsKey("en")) {
            return translationsByLocale.get("en");
        }
        return definition.getSlug();
    }

    private Map<Long, List<LabelTranslation>> groupTranslations(List<LabelTranslation> translations) {
        Map<Long, List<LabelTranslation>> grouped = new HashMap<>();
        for (LabelTranslation translation : translations) {
            grouped.computeIfAbsent(translation.getLabelId(), ignored -> new ArrayList<>()).add(translation);
        }
        return grouped;
    }

    private void assertCanMutateLabel(LabelDefinition definition,
                                      Skill skill,
                                      String currentUserId,
                                      Map<Long, NamespaceRole> userNsRoles,
                                      Set<String> platformRoles) {
        boolean isSuperAdmin = platformRoles != null && platformRoles.contains("SUPER_ADMIN");
        if (definition.getType() == LabelType.PRIVILEGED && !isSuperAdmin) {
            throw new DomainForbiddenException("label.no_permission");
        }
        if (isSuperAdmin) {
            return;
        }
        if (currentUserId == null) {
            throw new DomainForbiddenException("label.no_permission");
        }
        boolean isOwner = currentUserId.equals(skill.getOwnerId());
        NamespaceRole role = userNsRoles != null ? userNsRoles.get(skill.getNamespaceId()) : null;
        boolean isNamespaceAdmin = role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
        if (!isOwner && !isNamespaceAdmin) {
            throw new DomainForbiddenException("label.no_permission");
        }
    }

    private LabelDefinition getDefinitionBySlug(String slug) {
        return labelDefinitionRepository.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> new DomainNotFoundException("label.not_found", slug));
    }

    private List<Long> snapshotSkillIdsForLabel(Long labelId) {
        return skillLabelRepository.findByLabelId(labelId).stream()
                .map(SkillLabel::getSkillId)
                .distinct()
                .toList();
    }

    private String normalizeSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLocale(String locale) {
        return locale == null ? "" : locale.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
