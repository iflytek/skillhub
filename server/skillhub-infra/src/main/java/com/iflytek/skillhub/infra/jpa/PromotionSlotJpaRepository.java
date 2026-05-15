package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.promotion.PromotionSlot;
import com.iflytek.skillhub.domain.promotion.PromotionSlotRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA-backed implementation of {@link PromotionSlotRepository}.
 */
@Repository
public interface PromotionSlotJpaRepository extends JpaRepository<PromotionSlot, Long>, PromotionSlotRepository {

    @Override
    Optional<PromotionSlot> findBySlotCode(String slotCode);

    @Override
    boolean existsBySlotCode(String slotCode);
}
