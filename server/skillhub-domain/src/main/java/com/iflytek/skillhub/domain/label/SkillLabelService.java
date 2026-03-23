package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        this.maxLabelsPerSkill = maxLabelsPerSkill;
    }

    public List<SkillLabel> listSkillLabels(Long skillId) {
        return skillLabelRepository.findBySkillId(skillId);
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
}
