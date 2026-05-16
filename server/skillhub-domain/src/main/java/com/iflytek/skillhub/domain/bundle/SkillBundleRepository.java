package com.iflytek.skillhub.domain.bundle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for {@link SkillBundle}.
 */
public interface SkillBundleRepository {
    SkillBundle save(SkillBundle bundle);
    Optional<SkillBundle> findById(Long id);
    Optional<SkillBundle> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    Page<SkillBundle> findByOwnerId(String ownerId, Pageable pageable);
    Page<SkillBundle> findByBundleType(SkillBundleType bundleType, Pageable pageable);
    List<SkillBundle> findByIdIn(List<Long> ids);
    boolean existsByNamespaceIdAndSlug(Long namespaceId, String slug);
    void incrementDownloadCount(Long bundleId);
    void delete(SkillBundle bundle);
}
