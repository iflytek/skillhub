package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillLabelService {

    private final int maxLabelsPerSkill;

    private final SkillRepository skillRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final SkillLabelRepository skillLabelRepository;
    private final LabelPermissionChecker labelPermissionChecker;

    public SkillLabelService(SkillRepository skillRepository,
                             LabelDefinitionRepository labelDefinitionRepository,
                             SkillLabelRepository skillLabelRepository,
                             LabelPermissionChecker labelPermissionChecker,
                             @Value("${skillhub.label.max-per-skill:10}") int maxLabelsPerSkill) {
        this.skillRepository = skillRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.skillLabelRepository = skillLabelRepository;
        this.labelPermissionChecker = labelPermissionChecker;
        this.maxLabelsPerSkill = requirePositive(maxLabelsPerSkill, "skillhub.label.max-per-skill");
    }

    public List<SkillLabel> listSkillLabels(Long skillId) {
        return skillLabelRepository.findBySkillId(skillId);
    }

    public List<SkillLabel> listSkillLabelsBySkillIds(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedSkillIds = skillIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedSkillIds.isEmpty()) {
            return List.of();
        }
        return skillLabelRepository.findBySkillIdIn(normalizedSkillIds);
    }

    public List<SkillLabel> listByLabelId(Long labelId) {
        return skillLabelRepository.findByLabelId(labelId);
    }

    @Transactional
    public SkillLabel attachLabel(Long skillId,
                                  String labelSlug,
                                  String operatorId,
                                  Map<Long, NamespaceRole> userNamespaceRoles,
                                  Set<String> platformRoles) {
        Skill skill = findSkill(skillId);
        LabelDefinition labelDefinition = findLabel(labelSlug);
        requireSkillLabelPermission(skill, labelDefinition, operatorId, userNamespaceRoles, platformRoles);

        List<SkillLabel> existingLabels = skillLabelRepository.findBySkillId(skillId);
        if (existingLabels.size() >= maxLabelsPerSkill) {
            throw new DomainBadRequestException("label.skill.too_many", skillId, maxLabelsPerSkill);
        }
        return skillLabelRepository.findBySkillIdAndLabelId(skillId, labelDefinition.getId())
                .orElseGet(() -> skillLabelRepository.save(new SkillLabel(skillId, labelDefinition.getId(), operatorId)));
    }

    @Transactional
    public List<SkillLabel> attachLabels(Long skillId,
                                         List<String> labelSlugs,
                                         String operatorId,
                                         Map<Long, NamespaceRole> userNamespaceRoles,
                                         Set<String> platformRoles) {
        if (labelSlugs == null || labelSlugs.isEmpty()) {
            return List.of();
        }

        Skill skill = findSkill(skillId);
        List<String> normalizedSlugs = labelSlugs.stream()
                .map(LabelSlugValidator::normalize)
                .distinct()
                .toList();
        if (normalizedSlugs.isEmpty()) {
            return List.of();
        }

        Map<String, LabelDefinition> definitionsBySlug = labelDefinitionRepository.findBySlugIn(normalizedSlugs)
                .stream()
                .collect(Collectors.toMap(
                        definition -> definition.getSlug().toLowerCase(java.util.Locale.ROOT),
                        Function.identity()
                ));
        for (String normalizedSlug : normalizedSlugs) {
            if (!definitionsBySlug.containsKey(normalizedSlug.toLowerCase(java.util.Locale.ROOT))) {
                throw new DomainBadRequestException("label.not_found", normalizedSlug);
            }
        }

        List<LabelDefinition> labelDefinitions = normalizedSlugs.stream()
                .map(slug -> definitionsBySlug.get(slug.toLowerCase(java.util.Locale.ROOT)))
                .toList();
        for (LabelDefinition labelDefinition : labelDefinitions) {
            requireSkillLabelPermission(skill, labelDefinition, operatorId, userNamespaceRoles, platformRoles);
        }

        List<SkillLabel> existingLabels = skillLabelRepository.findBySkillId(skillId);
        Map<Long, SkillLabel> existingByLabelId = existingLabels.stream()
                .collect(Collectors.toMap(SkillLabel::getLabelId, Function.identity(), (left, right) -> left));
        int newLabelCount = (int) labelDefinitions.stream()
                .map(LabelDefinition::getId)
                .filter(labelId -> !existingByLabelId.containsKey(labelId))
                .count();
        if (existingLabels.size() + newLabelCount > maxLabelsPerSkill) {
            throw new DomainBadRequestException("label.skill.too_many", skillId, maxLabelsPerSkill);
        }

        List<SkillLabel> attachedLabels = new ArrayList<>(labelDefinitions.size());
        List<SkillLabel> labelsToSave = new ArrayList<>();
        for (LabelDefinition labelDefinition : labelDefinitions) {
            SkillLabel existingLabel = existingByLabelId.get(labelDefinition.getId());
            if (existingLabel != null) {
                attachedLabels.add(existingLabel);
            } else {
                SkillLabel newLabel = new SkillLabel(skillId, labelDefinition.getId(), operatorId);
                labelsToSave.add(newLabel);
                attachedLabels.add(newLabel);
            }
        }
        if (!labelsToSave.isEmpty()) {
            Map<Long, SkillLabel> savedByLabelId = skillLabelRepository.saveAll(labelsToSave).stream()
                    .collect(Collectors.toMap(SkillLabel::getLabelId, Function.identity(), (left, right) -> left));
            return attachedLabels.stream()
                    .map(label -> savedByLabelId.getOrDefault(label.getLabelId(), label))
                    .toList();
        }
        return attachedLabels;
    }

    @Transactional
    public void detachLabel(Long skillId,
                            String labelSlug,
                            String operatorId,
                            Map<Long, NamespaceRole> userNamespaceRoles,
                            Set<String> platformRoles) {
        Skill skill = findSkill(skillId);
        LabelDefinition labelDefinition = findLabel(labelSlug);
        requireSkillLabelPermission(skill, labelDefinition, operatorId, userNamespaceRoles, platformRoles);

        SkillLabel skillLabel = skillLabelRepository.findBySkillIdAndLabelId(skillId, labelDefinition.getId())
                .orElseThrow(() -> new DomainBadRequestException("label.skill.not_found", skillId, labelSlug));
        skillLabelRepository.delete(skillLabel);
    }

    private Skill findSkill(Long skillId) {
        return skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
    }

    private LabelDefinition findLabel(String labelSlug) {
        String normalizedSlug = LabelSlugValidator.normalize(labelSlug);
        return labelDefinitionRepository.findBySlugIgnoreCase(normalizedSlug)
                .orElseThrow(() -> new DomainBadRequestException("label.not_found", normalizedSlug));
    }

    private void requireSkillLabelPermission(Skill skill,
                                             LabelDefinition labelDefinition,
                                             String operatorId,
                                             Map<Long, NamespaceRole> userNamespaceRoles,
                                             Set<String> platformRoles) {
        if (!labelPermissionChecker.canManageSkillLabel(skill, labelDefinition, operatorId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("label.skill.no_permission");
        }
    }

    private int requirePositive(int value, String propertyName) {
        if (value <= 0) {
            throw new IllegalArgumentException(propertyName + " must be greater than 0");
        }
        return value;
    }
}
