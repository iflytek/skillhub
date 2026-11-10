package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.SitePresentationResponse;
import com.iflytek.skillhub.dto.SitePresentationSettings;
import com.iflytek.skillhub.dto.SitePresentationSettings.HomepageMode;
import com.iflytek.skillhub.dto.SitePresentationUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SitePresentationService {
    public static final String SETTING_KEY = "site.presentation";
    private static final Set<String> LOCALES = Set.of("en", "zh", "ko", "ru");
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
        if (!validator.validate(request).isEmpty()) {
            throw new DomainBadRequestException("error.site.presentation.invalidSettings");
        }
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
        next.put("homepageMode", settings.homepageMode().name());
        next.put("heroTitle", localized(settings.heroTitle(), 160));
        next.put("heroDescription", localized(settings.heroDescription(), 1000));
        next.put("showPopular", settings.showPopular());
        next.put("showLatest", settings.showLatest());
        next.put("showQuickStart", settings.showQuickStart());
        next.put("footerVisible", settings.footerVisible());
        Map<String, Object> previous = stored.getValue();
        stored.update(next, actor);
        repository.saveAndFlush(stored);
        audit.record(actor, "SITE_PRESENTATION_UPDATE", "SYSTEM_SETTING", stored.getId(), requestIds.current(),
                context.clientIp(), context.userAgent(),
                AuditDetail.builder().put("previous", previous).put("current", next).build());
        return response(stored);
    }

    private SitePresentationResponse response(SystemSetting stored) {
        Map<String, Object> values = stored.getValue();
        HomepageMode mode = "CATALOG".equals(values.get("homepageMode")) ? HomepageMode.CATALOG : HomepageMode.LANDING;
        var settings = new SitePresentationSettings(text(values.get("siteName"), 80),
                text(values.get("logoUrl"), 256), text(values.get("faviconUrl"), 256), mode,
                localized(values.get("heroTitle"), 160), localized(values.get("heroDescription"), 1000),
                visible(values, "showPopular"), visible(values, "showLatest"),
                visible(values, "showQuickStart"), visible(values, "footerVisible"));
        return new SitePresentationResponse(settings, stored.getVersion());
    }

    private boolean visible(Map<String, Object> values, String key) { return !Boolean.FALSE.equals(values.get(key)); }
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
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String locale : LOCALES) {
            String translated = text(source.get(locale), maxLength);
            if (translated != null) result.put(locale, translated);
        }
        return Map.copyOf(result);
    }
}
