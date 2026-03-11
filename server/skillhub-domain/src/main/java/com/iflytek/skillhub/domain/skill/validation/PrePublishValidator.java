package com.iflytek.skillhub.domain.skill.validation;

import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;

import java.util.List;

public interface PrePublishValidator {
    ValidationResult validate(SkillPackageContext context);

    record SkillPackageContext(
        List<PackageEntry> entries,
        SkillMetadata metadata,
        Long publisherId,
        Long namespaceId
    ) {}
}
