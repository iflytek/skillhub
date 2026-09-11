package com.iflytek.skillhub.domain.suite.bundle;

import java.time.Instant;
import java.util.Optional;

public interface SkillSuiteBundlePreviewSessionRepository {
    SkillSuiteBundlePreviewSession save(SkillSuiteBundlePreviewSession preview);
    void flush();
    Optional<SkillSuiteBundlePreviewSession> findById(String token);
    Optional<SkillSuiteBundlePreviewSession> findByIdForUpdate(String token);
    int expireReadyBefore(Instant threshold);
}
