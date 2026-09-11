package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface SkillSuiteBundlePreviewSessionJpaRepository
        extends JpaRepository<SkillSuiteBundlePreviewSession, String>, SkillSuiteBundlePreviewSessionRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT preview FROM SkillSuiteBundlePreviewSession preview WHERE preview.token = :token")
    Optional<SkillSuiteBundlePreviewSession> findByIdForUpdate(@Param("token") String token);

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
