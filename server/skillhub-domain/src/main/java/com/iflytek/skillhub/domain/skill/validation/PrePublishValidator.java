package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;

import java.util.List;

/**
 * Extension point for content-aware validation that runs after package parsing but before a skill
 * version is accepted for publishing.
 */
public interface PrePublishValidator {
    ValidationResult validate(SkillPackageContext context);

    record SkillPackageContext(
        List<PackageEntry> entries,
        SkillMetadata metadata,
        String publisherId,
        Long namespaceId
    ) {}
}
