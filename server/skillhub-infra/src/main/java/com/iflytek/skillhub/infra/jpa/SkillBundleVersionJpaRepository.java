package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.bundle.SkillBundleVersion;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillBundleVersionJpaRepository extends JpaRepository<SkillBundleVersion, Long>, SkillBundleVersionRepository {

    @Override
    Optional<SkillBundleVersion> findByBundleIdAndVersion(Long bundleId, String version);

    @Override
    List<SkillBundleVersion> findByBundleId(Long bundleId);

    @Override
    List<SkillBundleVersion> findByBundleIdAndStatus(Long bundleId, SkillBundleVersionStatus status);
}
