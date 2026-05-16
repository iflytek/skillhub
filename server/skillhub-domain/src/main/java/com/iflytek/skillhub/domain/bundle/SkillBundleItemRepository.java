package com.iflytek.skillhub.domain.bundle;

import java.util.List;

/**
 * Persistence contract for {@link SkillBundleItem}.
 */
public interface SkillBundleItemRepository {
    SkillBundleItem save(SkillBundleItem item);
    List<SkillBundleItem> findByBundleVersionId(Long bundleVersionId);
    void deleteByBundleVersionId(Long bundleVersionId);
}
