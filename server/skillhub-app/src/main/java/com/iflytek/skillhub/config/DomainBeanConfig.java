package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.promotion.PromotionCampaignRepository;
import com.iflytek.skillhub.domain.promotion.PromotionCampaignService;
import com.iflytek.skillhub.domain.promotion.PromotionEventLogRepository;
import com.iflytek.skillhub.domain.promotion.PromotionSlotRepository;
import com.iflytek.skillhub.domain.promotion.PromotionTargetGuard;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires application-level Spring beans that adapt configurable infrastructure into domain-facing
 * ports.
 */
@Configuration
public class DomainBeanConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SkillMetadataParser skillMetadataParser() {
        return new SkillMetadataParser();
    }

    @Bean
    public SkillPackageValidator skillPackageValidator(SkillMetadataParser skillMetadataParser,
                                                       SkillPublishProperties skillPublishProperties) {
        return new SkillPackageValidator(
                skillMetadataParser,
                skillPublishProperties.getMaxFileCount(),
                skillPublishProperties.getMaxSingleFileSize(),
                skillPublishProperties.getMaxPackageSize(),
                skillPublishProperties.getAllowedFileExtensions()
        );
    }

    @Bean
    public VisibilityChecker visibilityChecker() {
        return new VisibilityChecker();
    }

    @Bean
    public PromotionCampaignService promotionCampaignService(PromotionSlotRepository slotRepository,
                                                             PromotionCampaignRepository campaignRepository,
                                                             PromotionEventLogRepository eventLogRepository,
                                                             PromotionTargetGuard targetGuard) {
        return new PromotionCampaignService(slotRepository, campaignRepository, eventLogRepository, targetGuard);
    }
}
