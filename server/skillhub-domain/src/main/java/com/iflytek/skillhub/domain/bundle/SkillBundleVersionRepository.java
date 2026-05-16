package com.iflytek.skillhub.domain.bundle;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link SkillBundleVersion}.
 */
public interface SkillBundleVersionRepository {
    SkillBundleVersion save(SkillBundleVersion version);
    Optional<SkillBundleVersion> findById(Long id);
    Optional<SkillBundleVersion> findByBundleIdAndVersion(Long bundleId, String version);
    List<SkillBundleVersion> findByBundleId(Long bundleId);
    List<SkillBundleVersion> findByBundleIdAndStatus(Long bundleId, SkillBundleVersionStatus status);
    void delete(SkillBundleVersion version);
}
