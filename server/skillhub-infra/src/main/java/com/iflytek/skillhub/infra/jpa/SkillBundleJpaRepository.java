package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.bundle.SkillBundle;
import com.iflytek.skillhub.domain.bundle.SkillBundleRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillBundleJpaRepository extends JpaRepository<SkillBundle, Long>, SkillBundleRepository {

    @Override
    Optional<SkillBundle> findByNamespaceIdAndSlug(Long namespaceId, String slug);

    @Override
    Page<SkillBundle> findByOwnerId(String ownerId, Pageable pageable);

    @Override
    Page<SkillBundle> findByBundleType(SkillBundleType bundleType, Pageable pageable);

    @Override
    List<SkillBundle> findByIdIn(List<Long> ids);

    @Override
    boolean existsByNamespaceIdAndSlug(Long namespaceId, String slug);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SkillBundle b SET b.downloadCount = b.downloadCount + 1 WHERE b.id = :id")
    void incrementDownloadCount(@Param("id") Long bundleId);
}
