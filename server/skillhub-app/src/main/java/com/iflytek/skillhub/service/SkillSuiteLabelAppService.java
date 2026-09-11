package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.SkillSuiteLabel;
import com.iflytek.skillhub.domain.label.SkillSuiteLabelService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteQueryService;
import com.iflytek.skillhub.domain.suite.SkillSuiteRepository;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.SkillLabelDto;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates Suite label visibility, mutations, localization, and Suite-scoped audit. */
@Service
public class SkillSuiteLabelAppService {

    private final NamespaceRepository namespaceRepository;
    private final SkillSuiteRepository suiteRepository;
    private final SkillSuiteQueryService suiteQueryService;
    private final SkillSuiteLabelService suiteLabelService;
    private final SkillSuiteLabelProjectionService projectionService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public SkillSuiteLabelAppService(
            NamespaceRepository namespaceRepository,
            SkillSuiteRepository suiteRepository,
            SkillSuiteQueryService suiteQueryService,
            SkillSuiteLabelService suiteLabelService,
            SkillSuiteLabelProjectionService projectionService,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor
    ) {
        this.namespaceRepository = namespaceRepository;
        this.suiteRepository = suiteRepository;
        this.suiteQueryService = suiteQueryService;
        this.suiteLabelService = suiteLabelService;
        this.projectionService = projectionService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    @Transactional(readOnly = true)
    public List<SkillLabelDto> listLabels(
            String namespaceSlug,
            String suiteSlug,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuite suite = resolveSuite(namespaceSlug, suiteSlug);
        Map<Long, NamespaceRole> safeNamespaceRoles = roles(namespaceRoles);
        Set<String> safePlatformRoles = roles(platformRoles);
        if (!canManageContainer(suite, userId, safeNamespaceRoles, safePlatformRoles)) {
            suiteQueryService.getDetail(
                    namespaceSlug, suiteSlug, null, userId, safeNamespaceRoles, safePlatformRoles);
        }
        return projectionService.labelsBySuiteIds(List.of(suite.getId()))
                .getOrDefault(suite.getId(), List.of());
    }

    @Transactional
    public SkillLabelDto attachLabel(
            String namespaceSlug,
            String suiteSlug,
            String labelSlug,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            AuditRequestContext auditContext
    ) {
        SkillSuite suite = resolveSuite(namespaceSlug, suiteSlug);
        SkillSuiteLabel attached = suiteLabelService.attachLabel(
                suite.getId(), labelSlug, userId, roles(namespaceRoles), roles(platformRoles));
        recordAudit("SKILL_SUITE_LABEL_ATTACH", userId, suite.getId(), labelSlug, auditContext);
        return projectionService.labelsBySuiteIds(List.of(attached.getSuiteId()))
                .getOrDefault(attached.getSuiteId(), List.of()).stream()
                .filter(label -> label.slug().equalsIgnoreCase(labelSlug.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Attached Suite label projection is missing"));
    }

    @Transactional
    public MessageResponse detachLabel(
            String namespaceSlug,
            String suiteSlug,
            String labelSlug,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            AuditRequestContext auditContext
    ) {
        SkillSuite suite = resolveSuite(namespaceSlug, suiteSlug);
        suiteLabelService.detachLabel(
                suite.getId(), labelSlug, userId, roles(namespaceRoles), roles(platformRoles));
        recordAudit("SKILL_SUITE_LABEL_DETACH", userId, suite.getId(), labelSlug, auditContext);
        return new MessageResponse("Suite label detached");
    }

    private SkillSuite resolveSuite(String namespaceSlug, String suiteSlug) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceSlug));
        return suiteRepository.findByNamespaceIdAndSlug(namespace.getId(), suiteSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteSlug));
    }

    private boolean canManageContainer(
            SkillSuite suite,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        if (platformRoles.contains("SUPER_ADMIN")) {
            return true;
        }
        NamespaceRole role = namespaceRoles.get(suite.getNamespaceId());
        return role == NamespaceRole.OWNER
                || role == NamespaceRole.ADMIN
                || (role != null && userId != null && userId.equals(suite.getCreatedBy()));
    }

    private void recordAudit(
            String action,
            String userId,
            Long suiteId,
            String labelSlug,
            AuditRequestContext auditContext
    ) {
        auditLogService.record(
                userId,
                action,
                "SKILL_SUITE",
                suiteId,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                AuditDetail.of("labelSlug", labelSlug));
    }

    private Map<Long, NamespaceRole> roles(Map<Long, NamespaceRole> roles) {
        return roles == null ? Map.of() : roles;
    }

    private Set<String> roles(Set<String> roles) {
        return roles == null ? Set.of() : roles;
    }
}
