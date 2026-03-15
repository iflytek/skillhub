package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillVersionJpaRepository extends JpaRepository<SkillVersion, Long>, SkillVersionRepository {
    List<SkillVersion> findByIdIn(List<Long> ids);
    List<SkillVersion> findBySkillId(Long skillId);
    List<SkillVersion> findBySkillIdIn(List<Long> skillIds);
    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);

    @Override
    default List<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status) {
        return findBySkillIdAndStatusOrderByCreatedAtDesc(skillId, status);
    }

    List<SkillVersion> findBySkillIdAndStatusOrderByCreatedAtDesc(Long skillId, SkillVersionStatus status);
    Page<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status, Pageable pageable);
}
