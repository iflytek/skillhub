package com.iflytek.skillhub.domain.label;

import java.util.List;
import java.util.Optional;

public interface SkillLabelRepository {
    List<SkillLabel> findBySkillId(Long skillId);
    List<SkillLabel> findBySkillIdIn(List<Long> skillIds);
    List<SkillLabel> findByLabelId(Long labelId);
    Optional<SkillLabel> findBySkillIdAndLabelId(Long skillId, Long labelId);
    long countBySkillId(Long skillId);
    SkillLabel save(SkillLabel skillLabel);
    void delete(SkillLabel skillLabel);
}
