package com.iflytek.skillhub.service.promotion;

import com.iflytek.skillhub.domain.promotion.PromotionCampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-minute lifecycle sweep: SCHEDULED -&gt; ACTIVE and ACTIVE -&gt; ENDED.
 * Disabled by default in tests via {@code skillhub.promotion.scheduler-enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "skillhub.promotion.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class PromotionCampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(PromotionCampaignScheduler.class);

    private final PromotionCampaignAppService appService;

    public PromotionCampaignScheduler(PromotionCampaignAppService appService) {
        this.appService = appService;
    }

    @Scheduled(cron = "${skillhub.promotion.scheduler-cron:0 * * * * *}")
    public void sweep() {
        try {
            PromotionCampaignService.SchedulerSweepResult result = appService.sweepLifecycle();
            if (result.activated() > 0 || result.ended() > 0) {
                log.info("Promotion lifecycle sweep activated={}, ended={}", result.activated(), result.ended());
            }
        } catch (RuntimeException ex) {
            log.warn("Promotion lifecycle sweep failed", ex);
        }
    }
}
