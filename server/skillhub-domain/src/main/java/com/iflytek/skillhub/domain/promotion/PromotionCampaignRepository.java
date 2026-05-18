package com.iflytek.skillhub.domain.promotion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for {@link PromotionCampaign}.
 */
public interface PromotionCampaignRepository {
    PromotionCampaign save(PromotionCampaign campaign);

    Optional<PromotionCampaign> findById(Long id);

    Page<PromotionCampaign> findByStatus(PromotionCampaignStatus status, Pageable pageable);

    List<PromotionCampaign> findActiveBySlot(String slotCode, Instant now);

    /**
     * Returns campaigns that may consume slot capacity inside the requested window.
     *
     * <p>PENDING_REVIEW campaigns are intentionally excluded because capacity is
     * reserved at approval time, not at submission time.
     */
    List<PromotionCampaign> findCapacityCandidates(String slotCode, Instant startsAt, Instant endsAt);

    List<PromotionCampaign> findReadyToActivate(Instant now);

    /**
     * Optimistically transitions a campaign's status. Returns the number of rows updated;
     * 0 means another writer beat us to the punch.
     */
    int updateStatusWithVersion(Long id,
                                PromotionCampaignStatus newStatus,
                                String reviewedBy,
                                String reviewComment,
                                Integer expectedVersion);

    int markActiveAsEnded(Instant now);
}
