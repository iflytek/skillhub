package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.SitePresentationResponse;
import com.iflytek.skillhub.dto.SitePresentationSection;
import com.iflytek.skillhub.dto.SitePresentationSettings;
import com.iflytek.skillhub.dto.SitePresentationUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SitePresentationService {
    public static final String SETTING_KEY = "site.presentation";
    private final SystemSettingRepository repository;
    private final SitePresentationAssetService assets;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final Validator validator;

    public SitePresentationService(SystemSettingRepository repository, SitePresentationAssetService assets,
            AuditLogService audit, RequestIdAccessor requestIds, Validator validator) {
        this.repository = repository;
        this.assets = assets;
        this.audit = audit;
        this.requestIds = requestIds;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public SitePresentationResponse current() {
        return repository.findBySettingKey(SETTING_KEY)
                .map(this::response)
                .orElseGet(() -> new SitePresentationResponse(SitePresentationSettings.defaults(), 0));
    }

    @Transactional
    public SitePresentationResponse update(SitePresentationUpdateRequest request, String actor,
                                           AuditRequestContext context) {
        if (!validator.validate(request).isEmpty()) throw invalidSettings();
        validateSections(request.settings().sections());
        Map<String, String> links = validateLinks(request.settings().links());
        // Seeded by the migration, so concurrent first updates share the same optimistic lock.
        SystemSetting stored = repository.findBySettingKey(SETTING_KEY)
                .orElseThrow(() -> new IllegalStateException("Missing required site.presentation setting"));
        if (stored.getVersion() != request.version()) {
            throw new DomainConflictException("error.system.settings.version.conflict");
        }
        var settings = request.settings();
        String logo = nullableText(settings.logoUrl());
        String favicon = nullableText(settings.faviconUrl());
        assets.validateReference(logo);
        assets.validateReference(favicon);
        Map<String, Object> next = new LinkedHashMap<>();
        putText(next, "siteName", settings.siteName());
        putText(next, "logoUrl", logo);
        putText(next, "faviconUrl", favicon);
        Map<String, Object> sections = new LinkedHashMap<>();
        for (String id : SitePresentationSettings.SECTION_IDS) {
            SitePresentationSection section = settings.sections().getOrDefault(id, SitePresentationSection.defaults());
            sections.put(id, Map.of("enabled", section.enabled(), "texts", normalizedTexts(id, section.texts()),
                    "items", section.items() == null ? Map.of() : Map.copyOf(section.items())));
        }
        next.put("sections", sections);
        next.put("links", links);
        Map<String, Object> previous = stored.getValue();
        stored.update(next, actor);
        repository.saveAndFlush(stored);
        audit.record(actor, "SITE_PRESENTATION_UPDATE", "SYSTEM_SETTING", stored.getId(), requestIds.current(),
                context.clientIp(), context.userAgent(),
                AuditDetail.builder().put("previous", previous).put("current", next).build());
        return response(stored);
    }

    private void validateSections(Map<String, SitePresentationSection> sections) {
        for (var entry : sections.entrySet()) {
            String id = entry.getKey();
            var section = entry.getValue();
            if (!SitePresentationSchema.TEXT_SLOTS.containsKey(id)
                    || !SitePresentationSchema.TEXT_SLOTS.get(id).containsAll(section.texts().keySet())
                    || section.items() != null && !SitePresentationSchema.ITEM_SLOTS.get(id).containsAll(section.items().keySet())) {
                throw invalidSettings();
            }
            for (var slot : section.texts().entrySet()) {
                if (slot.getValue().values().stream().anyMatch(value -> value.length() > SitePresentationSchema.textLimit(slot.getKey()))) {
                    throw invalidSettings();
                }
            }
        }
    }

    private Map<String, String> validateLinks(Map<String, String> links) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (var link : links.entrySet()) {
            if (!SitePresentationSchema.LINK_SLOTS.contains(link.getKey())) throw invalidSettings();
            if (link.getValue().isEmpty()) continue;
            String url = SitePresentationLinks.safeUrl(link.getValue());
            if (url == null) throw new DomainBadRequestException("error.site.presentation.invalidLink");
            normalized.put(link.getKey(), url);
        }
        return Map.copyOf(normalized);
    }

    private SitePresentationResponse response(SystemSetting stored) {
        Map<String, Object> values = stored.getValue();
        Map<String, SitePresentationSection> sections = new LinkedHashMap<>();
        boolean legacy = !values.containsKey("sections");
        Map<?, ?> source = map(values.get("sections"));
        for (String id : SitePresentationSettings.SECTION_IDS) {
            Map<?, ?> section = map(source.get(id));
            Map<String, Map<String, String>> texts = normalizedTexts(id, section.get("texts"));
            boolean enabled = !Boolean.FALSE.equals(section.get("enabled"));
            if (legacy) {
                enabled = legacyEnabled(values, id);
                if (id.equals("hero")) {
                    texts = normalizedTexts(id, Map.of("title", map(values.get("heroTitle")),
                            "description", map(values.get("heroDescription"))));
                }
            }
            Map<String, Boolean> items = new LinkedHashMap<>();
            Map<?, ?> rawItems = map(section.get("items"));
            for (String item : SitePresentationSchema.ITEM_SLOTS.get(id)) {
                if (rawItems.get(item) instanceof Boolean flag) items.put(item, flag);
            }
            sections.put(id, new SitePresentationSection(enabled, texts, Map.copyOf(items)));
        }
        Map<String, String> links = new LinkedHashMap<>();
        Map<?, ?> rawLinks = map(values.get("links"));
        for (String slot : SitePresentationSchema.LINK_SLOTS) {
            if (rawLinks.get(slot) instanceof String raw) {
                String safe = SitePresentationLinks.safeUrl(raw);
                if (safe != null) links.put(slot, safe);
            }
        }
        var settings = new SitePresentationSettings(text(values.get("siteName"), 80),
                text(values.get("logoUrl"), 256), text(values.get("faviconUrl"), 256),
                Map.copyOf(sections), Map.copyOf(links));
        return new SitePresentationResponse(settings, stored.getVersion());
    }

    private boolean legacyEnabled(Map<String, Object> values, String id) {
        String old = switch (id) {
            case "popular" -> "showPopular";
            case "latest" -> "showLatest";
            case "quickStart" -> "showQuickStart";
            case "footer" -> "footerVisible";
            default -> null;
        };
        return old == null || !Boolean.FALSE.equals(values.get(old));
    }

    private Map<String, Map<String, String>> normalizedTexts(String id, Object value) {
        Map<?, ?> source = map(value);
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (String slot : SitePresentationSchema.TEXT_SLOTS.get(id)) {
            Map<String, String> translated = localized(source.get(slot), SitePresentationSchema.textLimit(slot));
            if (!translated.isEmpty()) result.put(slot, translated);
        }
        return Map.copyOf(result);
    }

    private Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private String text(Object value, int maxLength) {
        if (!(value instanceof String string) || string.length() > maxLength) return null;
        return nullableText(string);
    }
    private String nullableText(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private void putText(Map<String, Object> target, String key, String value) {
        String normalized = nullableText(value);
        if (normalized != null) target.put(key, normalized);
    }
    private Map<String, String> localized(Object value, int maxLength) {
        Map<?, ?> source = map(value);
        Map<String, String> result = new LinkedHashMap<>();
        for (String locale : SitePresentationSchema.LOCALES) {
            String translated = text(source.get(locale), maxLength);
            if (translated != null) result.put(locale, translated);
        }
        return Map.copyOf(result);
    }
    private DomainBadRequestException invalidSettings() {
        return new DomainBadRequestException("error.site.presentation.invalidSettings");
    }
}
