package com.iflytek.skillhub.domain.skill;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for skill version history and publication-state queries.
 */
public interface SkillVersionRepository {
    Optional<SkillVersion> findById(Long id);
    default Optional<SkillVersion> findByIdForUpdate(Long id) {
        throw new UnsupportedOperationException("This repository does not provide row locking");
    }
    default Optional<SkillVersionStatus> findStatusByIdAndSkillId(Long id, Long skillId) {
        return findById(id)
                .filter(version -> version.getSkillId().equals(skillId))
                .map(SkillVersion::getStatus);
    }
    List<SkillVersion> findByIdIn(List<Long> ids);
    List<SkillVersion> findBySkillIdIn(List<Long> skillIds);
    default List<SkillVersion> findBySkillIdInAndVersionIn(List<Long> skillIds, List<String> versions) {
        return findBySkillIdIn(skillIds).stream()
                .filter(version -> versions.contains(version.getVersion()))
                .toList();
    }
    List<SkillVersion> findBySkillIdInAndStatus(List<Long> skillIds, SkillVersionStatus status);
    List<SkillVersion> findBySkillId(Long skillId);
    List<SkillVersion> findBySkillIdForUpdate(Long skillId);
    Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version);
    List<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status);
    SkillVersion save(SkillVersion version);
    void delete(SkillVersion version);
    void flush();
    void deleteBySkillId(Long skillId);
}
