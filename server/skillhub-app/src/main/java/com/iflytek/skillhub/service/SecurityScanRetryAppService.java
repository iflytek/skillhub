package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityAuditRepository;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.dto.SkillLifecycleMutationResponse;
import com.iflytek.skillhub.storage.ObjectStorageService;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityScanRetryAppService {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SecurityAuditRepository securityAuditRepository;
    private final SecurityScanService securityScanService;
    private final ObjectStorageService objectStorageService;
    private final AuditLogService auditLogService;

    public SecurityScanRetryAppService(SkillRepository skillRepository,
                                       SkillVersionRepository skillVersionRepository,
                                       SecurityAuditRepository securityAuditRepository,
                                       SecurityScanService securityScanService,
                                       ObjectStorageService objectStorageService,
                                       AuditLogService auditLogService) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.securityAuditRepository = securityAuditRepository;
        this.securityScanService = securityScanService;
        this.objectStorageService = objectStorageService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SkillLifecycleMutationResponse retry(Long skillId,
                                                Long versionId,
                                                String userId,
                                                Set<String> platformRoles,
                                                Map<Long, NamespaceRole> namespaceRoles,
                                                AuditRequestContext auditContext) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
        authorize(skill, userId, platformRoles, namespaceRoles);

        SkillVersionStatus observedStatus = skillVersionRepository.findStatusByIdAndSkillId(versionId, skillId)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionId));
        if (observedStatus != SkillVersionStatus.SCAN_FAILED
                && observedStatus != SkillVersionStatus.SCANNING) {
            throw new DomainBadRequestException("error.security.scan.retry.status", observedStatus);
        }
        if (!securityScanService.isEnabled()) {
            throw new DomainBadRequestException("error.security.scan.retry.disabled");
        }

        String bundleKey = bundleKey(skillId, versionId);
        if (observedStatus == SkillVersionStatus.SCAN_FAILED
                && !objectStorageService.exists(bundleKey)) {
            throw new DomainBadRequestException("error.security.scan.retry.bundleMissing");
        }

        SkillVersion version = skillVersionRepository.findByIdForUpdate(versionId)
                .filter(candidate -> candidate.getSkillId().equals(skillId))
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", versionId));

        if (version.getStatus() == SkillVersionStatus.SCANNING && hasActiveAttempt(versionId)) {
            return response(skillId, versionId);
        }
        if (version.getStatus() != SkillVersionStatus.SCAN_FAILED) {
            throw new DomainBadRequestException("error.security.scan.retry.status", version.getStatus());
        }

        ScanTask task = securityScanService.retryStoredBundleScan(version, bundleKey, userId);
        auditLogService.record(
                userId,
                "RETRY_SECURITY_SCAN",
                "SKILL_VERSION",
                versionId,
                null,
                auditContext.clientIp(),
                auditContext.userAgent(),
                AuditDetail.of("taskId", task.taskId(), "version", version.getVersion())
        );
        return response(skillId, versionId);
    }

    private void authorize(Skill skill,
                           String userId,
                           Set<String> platformRoles,
                           Map<Long, NamespaceRole> namespaceRoles) {
        Set<String> roles = platformRoles != null ? platformRoles : Set.of();
        Map<Long, NamespaceRole> memberships = namespaceRoles != null ? namespaceRoles : Map.of();
        NamespaceRole namespaceRole = memberships.get(skill.getNamespaceId());
        boolean allowed = skill.getOwnerId().equals(userId)
                || namespaceRole == NamespaceRole.OWNER
                || namespaceRole == NamespaceRole.ADMIN
                || roles.contains("SUPER_ADMIN")
                || roles.contains("SKILL_ADMIN");
        if (!allowed) {
            throw new DomainForbiddenException("error.forbidden");
        }
    }

    private boolean hasActiveAttempt(Long versionId) {
        return securityAuditRepository
                .findLatestActiveByVersionIdAndScannerType(versionId, ScannerType.SKILL_SCANNER)
                .filter(audit -> audit.getScannedAt() == null)
                .isPresent();
    }

    private String bundleKey(Long skillId, Long versionId) {
        return String.format("packages/%d/%d/bundle.zip", skillId, versionId);
    }

    private SkillLifecycleMutationResponse response(Long skillId, Long versionId) {
        return new SkillLifecycleMutationResponse(skillId, versionId, "RETRY_SECURITY_SCAN", "SCANNING");
    }
}
