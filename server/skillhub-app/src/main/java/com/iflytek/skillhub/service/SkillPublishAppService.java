package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillPublishAppService {

    private final SkillPublishService skillPublishService;
    private final SkillLabelAppService skillLabelAppService;

    public SkillPublishAppService(SkillPublishService skillPublishService,
                                  SkillLabelAppService skillLabelAppService) {
        this.skillPublishService = skillPublishService;
        this.skillLabelAppService = skillLabelAppService;
    }

    @Transactional
    public PublishOutcome publishFromEntries(String namespace,
                                             List<PackageEntry> entries,
                                             String publisherId,
                                             SkillVisibility visibility,
                                             Set<String> platformRoles,
                                             boolean confirmWarnings,
                                             String summaryOverride,
                                             List<String> labelSlugs,
                                             Map<Long, NamespaceRole> userNsRoles) {
        SkillPublishService.PublishResult publishResult = skillPublishService.publishFromEntries(
                namespace,
                entries,
                publisherId,
                visibility,
                platformRoles,
                confirmWarnings,
                summaryOverride
        );
        if (labelSlugs != null && !labelSlugs.isEmpty()) {
            skillLabelAppService.attachLabels(
                    namespace,
                    publishResult.slug(),
                    labelSlugs,
                    publisherId,
                    userNsRoles,
                    null
            );
        }
        return new PublishOutcome(publishResult, labelSlugs == null ? List.of() : labelSlugs);
    }

    public record PublishOutcome(
            SkillPublishService.PublishResult publishResult,
            List<String> labels
    ) {}
}
