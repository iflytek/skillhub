package com.iflytek.skillhub.service;

import com.iflytek.skillhub.bootstrap.BuiltinSkillManifestLoader;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.dto.*;
import java.util.*;
import com.iflytek.skillhub.repository.RecommendationSkillQuery;
import org.springframework.stereotype.Component;

/** Reviewed starter collections resolve only installed, builtin-owned manifest entries. Reads never seed data. */
@Component
public class RecommendationDefaults {
    private final NamespaceRepository namespaces;
    private final SkillRepository skills;
    private final BuiltinSkillManifestLoader manifest;
    private final RecommendationSkillQuery query;
    private record Collection(String id, String coverStyle, String title, String description, List<String> slugs) {}
    private static final List<Collection> COLLECTIONS = List.of(
        new Collection("daily-work", "office", "把日常工作整理清楚", "从会议记录到任务安排，留下清晰的结论与下一步。", List.of("meeting-note-summarizer", "daily-standup-journal", "time-blocking-scheduler")),
        new Collection("design-docs", "design", "从界面到技术文档", "梳理界面、图示和文档，让想法更容易被理解。", List.of("frontend-design", "diagram-maker", "documentation-writer")),
        new Collection("learning", "learning", "建立自己的学习方法", "选择学习策略，通过练习和复盘检验理解。", List.of("study-strategy-selector", "retrieval-practice-generator", "exam-ready")),
        new Collection("writing", "content", "让内容表达更清晰", "组织故事与文章，减少空泛表达。", List.of("storytelling-advisor", "linkedin-post-formatter", "zero-slop")),
        new Collection("research", "research", "读懂资料，再核查结论", "阅读资料、比较选项，并检查关键事实。", List.of("cue-omni-reader", "ai-claim-checker", "decision-matrix")),
        new Collection("safe-tools", "security", "安装前多一步检查", "在使用外部能力之前了解风险与边界。", List.of("agentguard", "plugin-scanner")));
    public RecommendationDefaults(NamespaceRepository namespaces, SkillRepository skills, BuiltinSkillManifestLoader manifest, RecommendationSkillQuery query) {
        this.namespaces = namespaces; this.skills = skills; this.manifest = manifest; this.query = query;
    }
    public RecommendationContent content() {
        var global = namespaces.findBySlug("global");
        if (global.isEmpty()) return RecommendationContent.empty();
        var trusted = manifest.load().stream().map(BuiltinSkillManifestLoader.ManifestItem::slug).toList();
        Map<String, Long> ids = new HashMap<>();
        skills.findByNamespaceIdInAndSlugIn(List.of(global.get().getId()), trusted).stream()
                .filter(s -> "builtin-skill-publisher".equals(s.getOwnerId())).forEach(s -> ids.put(s.getSlug(), s.getId()));
        var eligible = new HashSet<>(query.eligible(new ArrayList<>(ids.values())));
        ids.values().removeIf(id -> !eligible.contains(id));
        var topics = COLLECTIONS.stream().map(c -> new RecommendationTopic(c.id(), c.title(), c.description(), null, c.coverStyle(), true,
                c.slugs().stream().map(ids::get).filter(Objects::nonNull).toList()))
                .filter(t -> !t.skillIds().isEmpty()).toList();
        return new RecommendationContent(topics, topics.stream().flatMap(t -> t.skillIds().stream()).distinct().limit(12).toList());
    }
}
