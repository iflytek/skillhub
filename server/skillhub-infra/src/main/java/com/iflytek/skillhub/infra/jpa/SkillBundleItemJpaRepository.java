package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.bundle.SkillBundleItem;
import com.iflytek.skillhub.domain.bundle.SkillBundleItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillBundleItemJpaRepository extends JpaRepository<SkillBundleItem, Long>, SkillBundleItemRepository {

    @Override
    List<SkillBundleItem> findByBundleVersionId(Long bundleVersionId);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SkillBundleItem i WHERE i.bundleVersionId = :versionId")
    void deleteByBundleVersionId(@Param("versionId") Long bundleVersionId);
}
