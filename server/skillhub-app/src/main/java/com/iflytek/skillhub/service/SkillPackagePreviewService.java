package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadata;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.SkillExamplesSummary;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import com.iflytek.skillhub.dto.SkillPackagePreviewResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillPackagePreviewService {
    private final SkillPackageValidator validator;
    private final SkillMetadataParser parser;

    public SkillPackagePreviewService(SkillPackageValidator validator, SkillMetadataParser parser) {
        this.validator = validator;
        this.parser = parser;
    }

    public SkillPackagePreviewResponse preview(List<PackageEntry> entries) {
        ValidationResult validation = validator.validate(entries);
        if (!validation.passed()) {
            throw new DomainBadRequestException("error.skill.publish.package.invalid", String.join(", ", validation.errors()));
        }
        PackageEntry skillMd = entries.stream().filter(entry -> "SKILL.md".equals(entry.path())).findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.skill.publish.skillMd.notFound"));
        SkillMetadata metadata = parser.parse(new String(skillMd.content(), StandardCharsets.UTF_8));
        SkillExamplesSummary examples = SkillExamplesSummary.recognize(entries);
        return new SkillPackagePreviewResponse(metadata.name(), metadata.displayName(), metadata.description(),
                metadata.shortDescription(), metadata.overview(), metadata.version(),
                examples.status(), examples.count());
    }
}
