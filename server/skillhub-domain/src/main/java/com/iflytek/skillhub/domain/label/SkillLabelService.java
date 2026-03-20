package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillLabelService {

    private static final int MAX_LABELS_PER_SKILL = 10;

    private final SkillRepository skillRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final SkillLabelRepository skillLabelRepository;
    private final LabelPermissionChecker labelPermissionChecker;

    public SkillLabelService(SkillRepository skillRepository,
                             LabelDefinitionRepository labelDefinitionRepository,
                             SkillLabelRepository skillLabelRepository,
                             LabelPermissionChecker labelPermissionChecker) {
        this.skillRepository = skillRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.skillLabelRepository = skillLabelRepository;
        this.labelPermissionChecker = labelPermissionChecker;
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
        if (existingLabels.size() >= MAX_LABELS_PER_SKILL) {
            throw new DomainBadRequestException("label.skill.too_many", skillId, MAX_LABELS_PER_SKILL);
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
        return labelDefinitionRepository.findBySlug(labelSlug)
                .orElseThrow(() -> new DomainBadRequestException("label.not_found", labelSlug));
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
