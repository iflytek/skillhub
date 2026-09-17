package com.iflytek.skillhub.domain.label;

import java.util.List;
import java.util.Optional;

public interface SkillSuiteLabelRepository {
    List<SkillSuiteLabel> findBySuiteId(Long suiteId);
    List<SkillSuiteLabel> findBySuiteIdIn(List<Long> suiteIds);
    List<SkillSuiteLabel> findByLabelId(Long labelId);
    Optional<SkillSuiteLabel> findBySuiteIdAndLabelId(Long suiteId, Long labelId);
    SkillSuiteLabel save(SkillSuiteLabel suiteLabel);
    void delete(SkillSuiteLabel suiteLabel);
}
