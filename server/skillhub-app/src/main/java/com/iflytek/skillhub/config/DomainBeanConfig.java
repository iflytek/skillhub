package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.bundle.SkillBundleDraftService;
import com.iflytek.skillhub.domain.bundle.SkillBundleItemRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleReviewService;
import com.iflytek.skillhub.domain.bundle.SkillBundleReviewTaskRepository;
import com.iflytek.skillhub.domain.bundle.SkillBundleVersionRepository;
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
    public SkillBundleDraftService skillBundleDraftService(SkillBundleRepository bundleRepository,
                                                           SkillBundleVersionRepository versionRepository,
                                                           SkillBundleItemRepository itemRepository,
                                                           SkillBundleDraftService.SkillBundleItemSourceResolver resolver) {
        return new SkillBundleDraftService(bundleRepository, versionRepository, itemRepository, resolver);
    }

    @Bean
    public SkillBundleReviewService skillBundleReviewService(SkillBundleRepository bundleRepository,
                                                             SkillBundleVersionRepository versionRepository,
                                                             SkillBundleReviewTaskRepository reviewTaskRepository) {
        return new SkillBundleReviewService(bundleRepository, versionRepository, reviewTaskRepository);
    }
}
