package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainBeanConfig {

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
}
