package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.RecommendationSkillQuery;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
// Eligibility and all batched projections must observe the same committed public state.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RecommendationService {
    public static final String KEY = "site.recommendations";
    private final SystemSettingRepository settings;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final SitePresentationAssetService assets;
    private final RecommendationSkillQuery query;
    private final SkillSearchAppService summaries;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final RecommendationDefaults defaults;
    private final SkillLabelProjectionService labels;

    public RecommendationService(SystemSettingRepository settings, ObjectMapper mapper, Validator validator,
            SitePresentationAssetService assets, RecommendationSkillQuery query, SkillSearchAppService summaries,
            AuditLogService audit, RequestIdAccessor requestIds, RecommendationDefaults defaults, SkillLabelProjectionService labels) {
        this.settings = settings; this.mapper = mapper; this.validator = validator; this.assets = assets;
        this.query = query; this.summaries = summaries; this.audit = audit; this.requestIds = requestIds; this.defaults = defaults; this.labels = labels;
    }
    public RecommendationAdminResponse current() { return response(row()); }
    public RecommendationPublicResponse published() { return project(publishedContent(row()), false); }
    public RecommendationTopicResponse topic(String id) {
        return project(publishedContent(row()), true).topics().stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new DomainNotFoundException("error.recommendations.topicNotFound"));
    }
    public PageResponse<SkillSummaryResponse> candidates(String q, int page, int size) {
        return candidates(q, page, size, "");
    }
    public PageResponse<SkillSummaryResponse> candidates(String q, int page, int size, String label) {
        if (page < 0 || page > 10000 || size < 1 || size > 50 || q.length() > 100 || label.length() > 100) throw invalid();
        return new PageResponse<>(projectSkills(query.candidates(q.strip(), page, size, label.strip())),
                query.count(q.strip(), label.strip()), page, size);
    }
    @Transactional
    public RecommendationAdminResponse save(RecommendationUpdateRequest request, String actor, AuditRequestContext context) {
        if (!validator.validate(request).isEmpty()) throw invalid();
        validate(request.draft());
        var row = checked(request.version());
        var next = new LinkedHashMap<>(row.getValue());
        next.put("draft", snapshot(request.draft()));
        persist(row, next, actor, context, "RECOMMENDATIONS_DRAFT_SAVE");
        return response(row);
    }
    @Transactional
    public RecommendationAdminResponse publish(long version, String actor, AuditRequestContext context) {
        var row = checked(version);
        if (!row.getValue().containsKey("draft")) throw invalid();
        var draft = content(row.getValue().get("draft"));
        validate(draft);
        var ids = ids(draft);
        if (query.eligible(ids).size() != ids.size()) throw new DomainConflictException("error.recommendations.unavailable");
        var next = new LinkedHashMap<>(row.getValue());
        next.put("draft", snapshot(draft));
        next.put("published", snapshot(draft));
        next.put("publishedAt", Instant.now().toString());
        persist(row, next, actor, context, "RECOMMENDATIONS_PUBLISH");
        return response(row);
    }
    private void persist(SystemSetting row, Map<String, Object> next, String actor, AuditRequestContext context, String action) {
        row.update(next, actor); settings.saveAndFlush(row);
        audit.record(actor, action, "SYSTEM_SETTING", row.getId(), requestIds.current(), context.clientIp(), context.userAgent(),
                AuditDetail.builder().put("version", row.getVersion()).build());
    }
    private SystemSetting row() { return settings.findBySettingKey(KEY).orElseThrow(() -> new IllegalStateException("Missing site.recommendations setting")); }
    private SystemSetting checked(long version) {
        var row = row();
        if (version != row.getVersion()) throw new DomainConflictException("error.system.settings.version.conflict");
        return row;
    }
    private RecommendationContent publishedContent(SystemSetting row) {
        return row.getValue().containsKey("published") ? content(row.getValue().get("published")) : defaults.content();
    }
    private RecommendationContent draftContent(SystemSetting row) {
        return row.getValue().containsKey("draft") ? content(row.getValue().get("draft")) : publishedContent(row);
    }
    private RecommendationContent content(Object value) { return mapper.convertValue(value, RecommendationContent.class); }
    private Map<String, Object> snapshot(RecommendationContent content) {
        try {
            // Match JSONB's untyped numeric representation. TokenBuffer convertValue retains Long
            // values while Hibernate's JSON deep copy reads small numbers as Integer; their unequal
            // Maps otherwise stay dirty across flushes and increment @Version after the response.
            return mapper.readValue(mapper.writeValueAsString(content), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode recommendation settings", exception);
        }
    }

    private RecommendationAdminResponse response(SystemSetting row) {
        var draft = draftContent(row); var published = publishedContent(row);
        var all = Stream.concat(ids(draft).stream(), ids(published).stream()).distinct().toList();
        return new RecommendationAdminResponse(row.getVersion(), draft, published,
                projectSkills(query.eligible(all)),
                (String) row.getValue().get("publishedAt"), !row.getValue().containsKey("published"));
    }
    private List<SkillSummaryResponse> projectSkills(List<Long> ids) {
        var attached = labels.labelsBySkillIds(ids);
        return summaries.mapVisibleSkillSummaries(ids, null).stream()
                .map(skill -> skill.withLabels(attached.getOrDefault(skill.id(), List.of()))).toList();
    }
    private void validate(RecommendationContent content) {
        if (!validator.validate(content).isEmpty() || !unique(content.featuredSkillIds())
                || !unique(content.topics().stream().map(RecommendationTopic::id).toList())) throw invalid();
        for (var topic : content.topics()) {
            if (!unique(topic.skillIds()) || topic.enabled() && topic.skillIds().isEmpty()) throw invalid();
            assets.validateReference(topic.coverUrl());
        }
    }
    private boolean unique(List<?> values) { return new HashSet<>(values).size() == values.size(); }
    private List<Long> ids(RecommendationContent c) {
        return Stream.concat(c.featuredSkillIds().stream(), c.topics().stream().flatMap(t -> t.skillIds().stream())).distinct().toList();
    }
    private RecommendationPublicResponse project(RecommendationContent c, boolean full) {
        Map<Long, SkillSummaryResponse> available = new LinkedHashMap<>();
        projectSkills(query.eligible(ids(c))).forEach(s -> available.put(s.id(), s));
        List<RecommendationTopicResponse> topics = c.topics().stream().filter(RecommendationTopic::enabled).map(t -> {
            var skills = t.skillIds().stream().map(available::get).filter(Objects::nonNull).toList();
            return new RecommendationTopicResponse(t.id(), t.title(), t.description(), t.coverUrl(), t.coverStyle(), skills.size(),
                    full ? skills : skills.stream().limit(3).toList());
        }).filter(t -> t.skillCount() > 0).toList();
        return new RecommendationPublicResponse(topics,
                c.featuredSkillIds().stream().map(available::get).filter(Objects::nonNull).toList());
    }
    private DomainBadRequestException invalid() { return new DomainBadRequestException("error.recommendations.invalid"); }
}
