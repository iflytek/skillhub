package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each new builtin and its one default label commit together; upgrades never restore removed labels. */
@Service
public class BuiltinSkillPublicationService {
    private final SkillPublishService publish;
    private final SkillLabelService labels;
    private final LabelDefinitionRepository definitions;
    public BuiltinSkillPublicationService(SkillPublishService publish, SkillLabelService labels, LabelDefinitionRepository definitions) {
        this.publish = publish; this.labels = labels; this.definitions = definitions;
    }
    @Transactional
    public SkillPublishService.PublishResult publishFromEntries(String namespace, List<PackageEntry> entries,
            String actor, SkillVisibility visibility, Set<String> roles, boolean confirm) {
        var result = publish.publishFromEntries(namespace, entries, actor, visibility, roles, confirm);
        String label = defaultLabel(result.slug());
        if (result.newSkill() && label != null && definitions.findBySlug(label)
                .filter(d -> d.getType() == LabelType.RECOMMENDED).isPresent()) {
            labels.attachLabel(result.skillId(), label, actor, Map.<Long, NamespaceRole>of(), roles);
        }
        return result;
    }
    static String defaultLabel(String slug) {
        return switch (slug) {
            case "meeting-note-summarizer", "daily-standup-journal", "time-blocking-scheduler" -> "productivity";
            case "frontend-design", "diagram-maker", "documentation-writer" -> "development";
            case "study-strategy-selector", "retrieval-practice-generator", "exam-ready" -> "learning";
            case "storytelling-advisor", "linkedin-post-formatter", "zero-slop" -> "writing";
            case "cue-omni-reader", "ai-claim-checker", "decision-matrix" -> "research";
            case "agentguard", "plugin-scanner" -> "security";
            default -> null;
        };
    }
}
