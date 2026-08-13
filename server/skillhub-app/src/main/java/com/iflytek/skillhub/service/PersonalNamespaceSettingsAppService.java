package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.PersonalNamespaceProvisioningService;
import com.iflytek.skillhub.domain.namespace.PersonalNamespaceSettings;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsResponse;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the personal-namespace provisioning policy to the admin console.
 */
@Service
public class PersonalNamespaceSettingsAppService {

    private static final String AUDIT_TARGET_TYPE = "SYSTEM_SETTING";
    private static final String AUDIT_ACTION_UPDATE = "SYSTEM_SETTING_PERSONAL_NAMESPACE_UPDATE";

    private static final List<String> SUPPORTED_PLACEHOLDERS = List.of(
            PersonalNamespaceSettings.PLACEHOLDER_USERNAME,
            PersonalNamespaceSettings.PLACEHOLDER_EMAIL_PREFIX,
            PersonalNamespaceSettings.PLACEHOLDER_USER_ID);

    private final PersonalNamespaceProvisioningService personalNamespaceProvisioningService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final ObjectMapper objectMapper;

    public PersonalNamespaceSettingsAppService(
            PersonalNamespaceProvisioningService personalNamespaceProvisioningService,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            ObjectMapper objectMapper) {
        this.personalNamespaceProvisioningService = personalNamespaceProvisioningService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PersonalNamespaceSettingsResponse get() {
        return toResponse(personalNamespaceProvisioningService.currentSettings());
    }

    @Transactional
    public PersonalNamespaceSettingsResponse update(PersonalNamespaceSettingsUpdateRequest request,
                                                    String actorUserId,
                                                    AuditRequestContext auditContext) {
        PersonalNamespaceSettings previous = personalNamespaceProvisioningService.currentSettings();
        PersonalNamespaceSettings updated = new PersonalNamespaceSettings(
                Boolean.TRUE.equals(request.enabled()),
                request.slugTemplate().trim(),
                request.displayNameTemplate().trim());

        personalNamespaceProvisioningService.updateSettings(updated, actorUserId);
        recordAudit(actorUserId, auditContext, previous, updated);
        return toResponse(updated);
    }

    private PersonalNamespaceSettingsResponse toResponse(PersonalNamespaceSettings settings) {
        return new PersonalNamespaceSettingsResponse(
                settings.enabled(),
                settings.slugTemplate(),
                settings.displayNameTemplate(),
                SUPPORTED_PLACEHOLDERS);
    }

    private void recordAudit(String actorUserId,
                             AuditRequestContext auditContext,
                             PersonalNamespaceSettings previous,
                             PersonalNamespaceSettings updated) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", describe(previous));
        detail.put("after", describe(updated));
        auditLogService.record(
                actorUserId,
                AUDIT_ACTION_UPDATE,
                AUDIT_TARGET_TYPE,
                null,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                toJson(detail));
    }

    private Map<String, Object> describe(PersonalNamespaceSettings settings) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("enabled", settings.enabled());
        described.put("slugTemplate", settings.slugTemplate());
        described.put("displayNameTemplate", settings.displayNameTemplate());
        return described;
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return null;
        }
    }
}
