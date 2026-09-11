package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SkillSuiteBundlePreviewSessionJpaRepository
        extends JpaRepository<SkillSuiteBundlePreviewSession, String>, SkillSuiteBundlePreviewSessionRepository {

    @Modifying
    @Query("""
            UPDATE SkillSuiteBundlePreviewSession preview
               SET preview.status = com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus.EXPIRED
             WHERE preview.status = :ready
               AND preview.expiresAt < :threshold
            """)
    int expireReadyBefore(
            @Param("threshold") Instant threshold,
            @Param("ready") SkillSuiteBundlePreviewStatus ready);

    @Override
    default int expireReadyBefore(Instant threshold) {
        return expireReadyBefore(threshold, SkillSuiteBundlePreviewStatus.PREVIEW_READY);
    }
}
