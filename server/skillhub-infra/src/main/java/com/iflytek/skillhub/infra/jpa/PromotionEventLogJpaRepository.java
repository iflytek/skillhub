package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.promotion.PromotionEventLog;
import com.iflytek.skillhub.domain.promotion.PromotionEventLogRepository;
import com.iflytek.skillhub.domain.promotion.PromotionEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed implementation of {@link PromotionEventLogRepository}.
 */
@Repository
public interface PromotionEventLogJpaRepository extends JpaRepository<PromotionEventLog, Long>,
                                                        PromotionEventLogRepository {

    @Override
    @Query("""
        SELECT COUNT(e) FROM PromotionEventLog e
        WHERE e.campaignId = :campaignId AND e.eventType = :type
    """)
    long countByCampaignAndType(@Param("campaignId") Long campaignId, @Param("type") PromotionEventType type);
}
