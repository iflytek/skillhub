package com.iflytek.skillhub.domain.promotion;

import java.util.Optional;

/**
 * Domain repository contract for {@link PromotionSlot}.
 */
public interface PromotionSlotRepository {
    Optional<PromotionSlot> findBySlotCode(String slotCode);

    PromotionSlot save(PromotionSlot slot);

    boolean existsBySlotCode(String slotCode);
}
