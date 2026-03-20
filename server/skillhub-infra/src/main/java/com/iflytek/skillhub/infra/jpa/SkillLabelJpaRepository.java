package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillLabelJpaRepository extends JpaRepository<SkillLabel, Long>, SkillLabelRepository {
    List<SkillLabel> findBySkillId(Long skillId);
    List<SkillLabel> findBySkillIdIn(List<Long> skillIds);
    List<SkillLabel> findByLabelId(Long labelId);
    Optional<SkillLabel> findBySkillIdAndLabelId(Long skillId, Long labelId);
    long countBySkillId(Long skillId);
}
