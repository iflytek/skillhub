package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA-backed repository for package files attached to one skill version.
 */
@Repository
public interface SkillFileJpaRepository extends JpaRepository<SkillFile, Long>, SkillFileRepository {
    List<SkillFile> findByVersionId(Long versionId);
    void deleteByVersionId(Long versionId);
}
