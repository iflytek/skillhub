package com.iflytek.skillhub.domain.suite;

import java.util.List;
import java.util.Optional;

/** Persistence contract for Suite containers. */
public interface SkillSuiteRepository {
    Optional<SkillSuite> findById(Long id);
    Optional<SkillSuite> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    List<SkillSuite> findByIdIn(List<Long> ids);
    List<SkillSuite> findByNamespaceId(Long namespaceId);
    SkillSuite save(SkillSuite suite);
    void delete(SkillSuite suite);
    void incrementInstallRequestCount(Long suiteId);
}
