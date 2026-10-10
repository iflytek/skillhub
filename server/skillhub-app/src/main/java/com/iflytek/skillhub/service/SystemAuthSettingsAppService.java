package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRule;
import com.iflytek.skillhub.auth.settings.ExternalRoleGrantRuleRepository;
import com.iflytek.skillhub.auth.settings.InitialExternalRoleGrantService;
import com.iflytek.skillhub.auth.settings.LocalAuthSettings;
import com.iflytek.skillhub.auth.settings.LocalAuthSettingsService;
import com.iflytek.skillhub.auth.settings.SystemSetting;
import com.iflytek.skillhub.auth.settings.SystemSettingRepository;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.ExternalRoleGrantCreateRequest;
import com.iflytek.skillhub.dto.ExternalRoleGrantRuleResponse;
import com.iflytek.skillhub.dto.ExternalRoleGrantUpdateRequest;
import com.iflytek.skillhub.dto.PlatformRoleResponse;
import com.iflytek.skillhub.dto.SystemAuthSettingsResponse;
import com.iflytek.skillhub.dto.SystemAuthSettingsUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAuthSettingsAppService {
    private static final Pattern PROVIDER = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final LocalAuthSettingsService localSettings;
    private final SystemSettingRepository settings;
    private final ExternalRoleGrantRuleRepository rules;
    private final RoleRepository roles;
    private final AuditLogService auditLog;
    private final RequestIdAccessor requestIds;

    public SystemAuthSettingsAppService(LocalAuthSettingsService localSettings,
                                        SystemSettingRepository settings,
                                        ExternalRoleGrantRuleRepository rules,
                                        RoleRepository roles,
                                        AuditLogService auditLog,
                                        RequestIdAccessor requestIds) {
        this.localSettings = localSettings;
        this.settings = settings;
        this.rules = rules;
        this.roles = roles;
        this.auditLog = auditLog;
        this.requestIds = requestIds;
    }

    public SystemAuthSettingsResponse getLocalSettings() {
        return toResponse(localSettings.current());
    }

    @Transactional
    public SystemAuthSettingsResponse updateLocalSettings(SystemAuthSettingsUpdateRequest request,
                                                          String actorUserId,
                                                          AuditRequestContext context) {
        SystemSetting setting = settings.findBySettingKey(LocalAuthSettingsService.SETTING_KEY)
                .orElseThrow(() -> new IllegalStateException("Missing required auth.local setting"));
        if (setting.getVersion() != request.version()) {
            throw new DomainConflictException("error.system.settings.version.conflict");
        }
        Map<String, Object> previous = setting.getValue();
        setting.update(Map.of(
                LocalAuthSettingsService.PASSWORD_LOGIN_KEY, request.passwordLoginEnabled(),
                LocalAuthSettingsService.SELF_REGISTRATION_KEY, request.selfRegistrationEnabled()), actorUserId);
        settings.saveAndFlush(setting);
        record(actorUserId, "SYSTEM_AUTH_SETTINGS_UPDATE", "SYSTEM_SETTING", setting.getId(), context,
                AuditDetail.builder().put("previous", previous).put("current", setting.getValue()).build());
        return getLocalSettings();
    }

    public List<PlatformRoleResponse> listRoles() {
        return roles.findAll().stream().filter(Role::isSystem)
                .sorted(Comparator.comparing(Role::getCode))
                .map(role -> new PlatformRoleResponse(role.getCode(), role.getName()))
                .toList();
    }

    public List<ExternalRoleGrantRuleResponse> listRules() {
        return rules.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExternalRoleGrantRuleResponse createRule(ExternalRoleGrantCreateRequest request,
                                                     String actorUserId,
                                                     AuditRequestContext context) {
        String provider = validProvider(request.providerCode());
        String email = validEmail(request.email());
        if (rules.existsByProviderCodeAndNormalizedEmailAndStatus(
                provider, email, ExternalRoleGrantRule.Status.ACTIVE)) {
            throw new DomainConflictException("error.system.roleGrant.duplicate");
        }
        Role role = validRole(request.roleCode());
        ExternalRoleGrantRule rule;
        try {
            rule = rules.saveAndFlush(new ExternalRoleGrantRule(provider, email, role, actorUserId));
        } catch (DataIntegrityViolationException duplicate) {
            throw new DomainConflictException("error.system.roleGrant.duplicate");
        }
        record(actorUserId, "INITIAL_ROLE_RULE_CREATE", "EXTERNAL_ROLE_GRANT_RULE", rule.getId(), context,
                AuditDetail.builder().put("provider", provider).put("roleCode", role.getCode()).build());
        return toResponse(rule);
    }

    @Transactional
    public ExternalRoleGrantRuleResponse updateRule(long id, ExternalRoleGrantUpdateRequest request,
                                                     String actorUserId,
                                                     AuditRequestContext context) {
        ExternalRoleGrantRule rule = loadRule(id);
        requireActiveVersion(rule, request.version());
        String oldRole = rule.getRole().getCode();
        Role role = validRole(request.roleCode());
        rule.update(role, actorUserId);
        rules.saveAndFlush(rule);
        record(actorUserId, "INITIAL_ROLE_RULE_UPDATE", "EXTERNAL_ROLE_GRANT_RULE", rule.getId(), context,
                AuditDetail.builder().put("oldRole", oldRole).put("newRole", role.getCode()).build());
        return toResponse(rule);
    }

    @Transactional
    public ExternalRoleGrantRuleResponse disableRule(long id, long expectedVersion,
                                                      String actorUserId,
                                                      AuditRequestContext context) {
        ExternalRoleGrantRule rule = loadRule(id);
        requireActiveVersion(rule, expectedVersion);
        rule.disable(actorUserId);
        rules.saveAndFlush(rule);
        record(actorUserId, "INITIAL_ROLE_RULE_DISABLE", "EXTERNAL_ROLE_GRANT_RULE", rule.getId(), context,
                AuditDetail.of("status", rule.getStatus().name()));
        return toResponse(rule);
    }

    private ExternalRoleGrantRule loadRule(long id) {
        return rules.findById(id).orElseThrow(() -> new DomainNotFoundException("error.system.roleGrant.notFound"));
    }

    private void requireActiveVersion(ExternalRoleGrantRule rule, long expectedVersion) {
        if (rule.getStatus() != ExternalRoleGrantRule.Status.ACTIVE || rule.getVersion() != expectedVersion) {
            throw new DomainConflictException("error.system.roleGrant.conflict");
        }
    }

    private Role validRole(String code) {
        return roles.findByCode(code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT))
                .filter(Role::isSystem)
                .orElseThrow(() -> new DomainBadRequestException("error.system.roleGrant.invalidRole"));
    }

    private String validProvider(String value) {
        String normalized = InitialExternalRoleGrantService.normalize(value);
        if (!PROVIDER.matcher(normalized).matches()) {
            throw new DomainBadRequestException("error.system.roleGrant.invalidProvider");
        }
        return normalized;
    }

    private String validEmail(String value) {
        String normalized = InitialExternalRoleGrantService.normalize(value);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new DomainBadRequestException("error.system.roleGrant.invalidEmail");
        }
        return normalized;
    }

    private SystemAuthSettingsResponse toResponse(LocalAuthSettings value) {
        return new SystemAuthSettingsResponse(value.passwordLoginEnabled(), value.selfRegistrationEnabled(),
                value.version(), value.updatedAt());
    }

    private ExternalRoleGrantRuleResponse toResponse(ExternalRoleGrantRule rule) {
        return new ExternalRoleGrantRuleResponse(rule.getId(), rule.getProviderCode(), rule.getNormalizedEmail(),
                rule.getRole().getCode(), rule.getStatus().name(), rule.getMatchedSubject(),
                rule.getGrantedUserId(), rule.getGrantedAt(), rule.getVersion(), rule.getUpdatedAt());
    }

    private void record(String actor, String action, String targetType, Long targetId,
                        AuditRequestContext context, String detail) {
        auditLog.record(actor, action, targetType, targetId, requestIds.current(),
                context.clientIp(), context.userAgent(), detail);
    }
}
