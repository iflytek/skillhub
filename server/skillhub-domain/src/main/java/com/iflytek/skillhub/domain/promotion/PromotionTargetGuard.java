package com.iflytek.skillhub.domain.promotion;

/**
 * Validates whether a target (skill or skill bundle) is in a state that allows it
 * to be promoted: published, public, scanned and not flagged unsafe.
 *
 * <p>Implementation lives outside the domain module so that the domain layer can
 * stay decoupled from skill/bundle aggregates.
 */
public interface PromotionTargetGuard {
    void assertPromotable(PromotionTargetType targetType, Long targetId, Long targetVersionId);
}
