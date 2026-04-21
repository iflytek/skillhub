package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.ReviewService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.service.SkillGovernanceService;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.service.SkillReviewSubmitService;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.SkillLifecycleMutationResponse;
import com.iflytek.skillhub.dto.SkillVersionRereleaseRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates skill lifecycle mutations so controllers only handle transport
 * concerns and envelope assembly.
 */
@Service
public class SkillLifecycleAppService {

    private final NamespaceRepository namespaceRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillGovernanceService skillGovernanceService;
    private final ReviewService reviewService;
    private final SkillPublishService skillPublishService;
    private final SkillReviewSubmitService skillReviewSubmitService;
    private final AuditLogService auditLogService;
    private final SkillSlugResolutionService skillSlugResolutionService;

    public SkillLifecycleAppService(NamespaceRepository namespaceRepository,
                                    SkillVersionRepository skillVersionRepository,
                                    SkillGovernanceService skillGovernanceService,
                                    ReviewService reviewService,
                                    SkillPublishService skillPublishService,
                                    SkillReviewSubmitService skillReviewSubmitService,
                                    AuditLogService auditLogService,
                                    SkillSlugResolutionService skillSlugResolutionService) {
        this.namespaceRepository = namespaceRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillGovernanceService = skillGovernanceService;
        this.reviewService = reviewService;
        this.skillPublishService = skillPublishService;
        this.skillReviewSubmitService = skillReviewSubmitService;
        this.auditLogService = auditLogService;
        this.skillSlugResolutionService = skillSlugResolutionService;
    }

    @Transactional
    public SkillLifecycleMutationResponse archiveSkill(String namespace,
                                                       String slug,
                                                       AdminSkillActionRequest request,
                                                       String userId,
                                                       Map<Long, NamespaceRole> userNamespaceRoles,
                                                       AuditRequestContext auditContext) {
        Skill skill = findSkill(namespace, slug, userId);
        Skill archived = skillGovernanceService.archiveSkill(
                skill.getId(),
                userId,
                normalizeRoles(userNamespaceRoles),
                auditContext.clientIp(),
                auditContext.userAgent(),
                request != null ? request.reason() : null
        );
        return new SkillLifecycleMutationResponse(archived.getId(), null, "ARCHIVE", archived.getStatus().name());
    }

    @Transactional
    public SkillLifecycleMutationResponse unarchiveSkill(String namespace,
                                                         String slug,
                                                         String userId,
                                                         Map<Long, NamespaceRole> userNamespaceRoles,
                                                         AuditRequestContext auditContext) {
        Skill skill = findSkill(namespace, slug, userId);
        Skill restored = skillGovernanceService.unarchiveSkill(
                skill.getId(),
                userId,
                normalizeRoles(userNamespaceRoles),
                auditContext.clientIp(),
                auditContext.userAgent()
        );
        return new SkillLifecycleMutationResponse(restored.getId(), null, "UNARCHIVE", restored.getStatus().name());
    }

    @Transactional
    public SkillLifecycleMutationResponse deleteVersion(String namespace,
                                                        String slug,
                                                        String version,
                                                        String userId,
                                                        Map<Long, NamespaceRole> userNamespaceRoles,
                                                        AuditRequestContext auditContext) {
        Skill skill = findSkill(namespace, slug, userId);
        SkillVersion skillVersion = findVersion(skill.getId(), version);
        skillGovernanceService.deleteVersion(
                skill,
                skillVersion,
                userId,
                normalizeRoles(userNamespaceRoles),
                auditContext.clientIp(),
                auditContext.userAgent(),
                namespace
        );
        return new SkillLifecycleMutationResponse(skill.getId(), skillVersion.getId(), "DELETE_VERSION", version);
    }

    @Transactional
    public SkillLifecycleMutationResponse withdrawReview(String namespace,
                                                         String slug,
                                                         String version,
                                                         String userId,
                                                         AuditRequestContext auditContext) {
        Skill skill = findSkill(namespace, slug, userId);
        SkillVersion skillVersion = findVersion(skill.getId(), version);
        SkillVersion withdrawnVersion = reviewService.withdrawReview(skillVersion.getId(), userId);
        auditLogService.record(
                userId,
                "REVIEW_WITHDRAW",
                "SKILL_VERSION",
                skillVersion.getId(),
                null,
                auditContext.clientIp(),
                auditContext.userAgent(),
                "{\"version\":\"" + version.replace("\"", "\\\"") + "\"}"
        );
        return new SkillLifecycleMutationResponse(
                skill.getId(),
                skillVersion.getId(),
                "WITHDRAW_REVIEW",
                withdrawnVersion.getStatus().name()
        );
    }

    @Transactional
    public SkillLifecycleMutationResponse rereleaseVersion(String namespace,
                                                           String slug,
                                                           String version,
                                                           SkillVersionRereleaseRequest request,
                                                           String userId,
                                                           Map<Long, NamespaceRole> userNamespaceRoles,
                                                           AuditRequestContext auditContext) {
        Skill skill = findSkill(namespace, slug, userId);
        SkillVersion skillVersion = findVersion(skill.getId(), version);
        String targetVersion = request.targetVersion().trim();
        SkillPublishService.PublishResult result = skillPublishService.rereleasePublishedVersion(
                skill.getId(),
                skillVersion.getVersion(),
                targetVersion,
                userId,
                normalizeRoles(userNamespaceRoles),
                request.confirmWarnings()
        );
        auditLogService.record(
                userId,
                "RERELEASE_SKILL_VERSION",
                "SKILL_VERSION",
                skillVersion.getId(),
                null,
                auditContext.clientIp(),
                auditContext.userAgent(),
                "{\"sourceVersion\":\"" + version.replace("\"", "\\\"")
                        + "\",\"targetVersion\":\"" + targetVersion.replace("\"", "\\\"") + "\"}"
        );
        return new SkillLifecycleMutationResponse(
                result.skillId(),
                result.version().getId(),
                "RERELEASE_VERSION",
                result.version().getStatus().name()
        );
    }

    @Transactional
    public SkillLifecycleMutationResponse submitForReview(String namespace,
                                                           String slug,
                                                           String version,
                                                           String targetVisibility,
                                                           String userId,
                                                           Map<Long, NamespaceRole> userNamespaceRoles,
                                                           AuditRequestContext auditContext) {
        Skill skill = findSkill(namespace, slug, userId);
        SkillVersion skillVersion = findVersion(skill.getId(), version);
        skillReviewSubmitService.submitForReview(
                skill.getId(),
                skillVersion.getId(),
                com.iflytek.skillhub.domain.skill.SkillVisibility.valueOf(targetVisibility),
                userId,
                normalizeRoles(userNamespaceRoles)
        );
        auditLogService.record(
                userId,
                "SUBMIT_REVIEW",
                "SKILL_VERSION",
                skillVersion.getId(),
                null,
                auditContext.clientIp(),
                auditContext.userAgent(),
                "{\"version\":\"" + version.replace("\"", "\\\"") + "\",\"targetVisibility\":\"" + targetVisibility + "\"}"
        );
        return new SkillLifecycleMutationResponse(
                skill.getId(),
                skillVersion.getId(),
                "SUBMIT_REVIEW",
                "PENDING_REVIEW"
        );
    }

    @Transactional
    public SkillLifecycleMutationResponse confirmPublish(String namespace,
                                                          String slug,
                                                          String version,
                                                          String userId,
                                                          Map<Long, NamespaceRole> userNamespaceRoles,
                                                          AuditRequestContext auditContext) {
        Skill skill = findSkill(namespace, slug, userId);
        SkillVersion skillVersion = findVersion(skill.getId(), version);
        skillReviewSubmitService.confirmPublish(
                skill.getId(),
                skillVersion.getId(),
                userId,
                normalizeRoles(userNamespaceRoles)
        );
        auditLogService.record(
                userId,
                "CONFIRM_PUBLISH",
                "SKILL_VERSION",
                skillVersion.getId(),
                null,
                auditContext.clientIp(),
                auditContext.userAgent(),
                "{\"version\":\"" + version.replace("\"", "\\\"") + "\"}"
        );
        return new SkillLifecycleMutationResponse(
                skill.getId(),
                skillVersion.getId(),
                "CONFIRM_PUBLISH",
                "PUBLISHED"
        );
    }

    private Skill findSkill(String namespaceSlug, String skillSlug, String currentUserId) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace namespace = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", cleanNamespace));
        return skillSlugResolutionService.resolve(
                namespace.getId(),
                skillSlug,
                currentUserId,
                SkillSlugResolutionService.Preference.CURRENT_USER
        );
    }

    private SkillVersion findVersion(Long skillId, String version) {
        return skillVersionRepository.findBySkillIdAndVersion(skillId, version)
                .orElseThrow(() -> new DomainBadRequestException("error.skill.version.notFound", version));
    }

    private Map<Long, NamespaceRole> normalizeRoles(Map<Long, NamespaceRole> userNamespaceRoles) {
        return userNamespaceRoles != null ? userNamespaceRoles : Map.of();
    }
}
