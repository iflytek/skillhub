package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.promotion.PromotionCampaign;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignRepository;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * JPA-backed implementation of {@link PromotionCampaignRepository}, including
 * optimistic state transitions and the periodic SCHEDULED -&gt; ACTIVE -&gt; ENDED sweep.
 */
@Repository
public interface PromotionCampaignJpaRepository extends JpaRepository<PromotionCampaign, Long>,
                                                        PromotionCampaignRepository {

    @Override
    Page<PromotionCampaign> findByStatus(PromotionCampaignStatus status, Pageable pageable);

    @Query("""
        SELECT c FROM PromotionCampaign c
        WHERE c.slotCode = :slotCode
          AND c.status = com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus.ACTIVE
          AND c.startsAt <= :now
          AND c.endsAt   >  :now
        ORDER BY c.priority DESC, c.startsAt DESC, c.id DESC
    """)
    @Override
    List<PromotionCampaign> findActiveBySlot(@Param("slotCode") String slotCode, @Param("now") Instant now);

    @Query("""
        SELECT COUNT(c) FROM PromotionCampaign c
        WHERE c.slotCode = :slotCode
          AND c.status = com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus.ACTIVE
          AND c.startsAt <= :now
          AND c.endsAt   >  :now
    """)
    @Override
    long countActiveBySlot(@Param("slotCode") String slotCode, @Param("now") Instant now);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PromotionCampaign c
        SET c.status = :status,
            c.reviewedBy = :reviewedBy,
            c.reviewComment = :reviewComment,
            c.updatedAt = CURRENT_TIMESTAMP,
            c.version = c.version + 1
        WHERE c.id = :id AND c.version = :expectedVersion
    """)
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("status") PromotionCampaignStatus status,
                                @Param("reviewedBy") String reviewedBy,
                                @Param("reviewComment") String reviewComment,
                                @Param("expectedVersion") Integer expectedVersion);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PromotionCampaign c
        SET c.status = com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus.ACTIVE,
            c.updatedAt = CURRENT_TIMESTAMP,
            c.version = c.version + 1
        WHERE c.status = com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus.SCHEDULED
          AND c.startsAt <= :now
          AND c.endsAt   >  :now
    """)
    int markScheduledAsActive(@Param("now") Instant now);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PromotionCampaign c
        SET c.status = com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus.ENDED,
            c.updatedAt = CURRENT_TIMESTAMP,
            c.version = c.version + 1
        WHERE c.status = com.iflytek.skillhub.domain.promotion.PromotionCampaignStatus.ACTIVE
          AND c.endsAt <= :now
    """)
    int markActiveAsEnded(@Param("now") Instant now);
}
