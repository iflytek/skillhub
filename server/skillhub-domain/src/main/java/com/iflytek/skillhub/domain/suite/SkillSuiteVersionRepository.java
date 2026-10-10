package com.iflytek.skillhub.domain.suite;

import java.util.List;
import java.util.Optional;

/** Persistence contract for immutable Suite version snapshots. */
public interface SkillSuiteVersionRepository {
    Optional<SkillSuiteVersion> findById(Long id);
    Optional<SkillSuiteVersion> findByIdForDefinitionUpdate(Long id);
    Optional<SkillSuiteVersion> findBySuiteIdAndVersion(Long suiteId, String version);
    List<SkillSuiteVersion> findByIdIn(List<Long> ids);
    List<SkillSuiteVersion> findBySuiteId(Long suiteId);
    List<SkillSuiteVersion> findBySuiteIdAndStatus(Long suiteId, SkillSuiteVersionStatus status);
    SkillSuiteVersion save(SkillSuiteVersion version);
    void delete(SkillSuiteVersion version);
}
