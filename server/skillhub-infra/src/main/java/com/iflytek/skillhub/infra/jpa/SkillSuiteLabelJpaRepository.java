package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.label.SkillSuiteLabel;
import com.iflytek.skillhub.domain.label.SkillSuiteLabelRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillSuiteLabelJpaRepository
        extends JpaRepository<SkillSuiteLabel, Long>, SkillSuiteLabelRepository {
    List<SkillSuiteLabel> findBySuiteId(Long suiteId);
    List<SkillSuiteLabel> findBySuiteIdIn(List<Long> suiteIds);
    List<SkillSuiteLabel> findByLabelId(Long labelId);
    Optional<SkillSuiteLabel> findBySuiteIdAndLabelId(Long suiteId, Long labelId);
}
