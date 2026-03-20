package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionService;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PromotionPortalAppService {

    private final PromotionService promotionService;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final RbacService rbacService;
    private final AuditLogService auditLogService;

    public PromotionPortalAppService(PromotionService promotionService,
                                     PromotionRequestRepository promotionRequestRepository,
                                     SkillRepository skillRepository,
                                     SkillVersionRepository skillVersionRepository,
                                     NamespaceRepository namespaceRepository,
                                     UserAccountRepository userAccountRepository,
                                     RbacService rbacService,
                                     AuditLogService auditLogService) {
        this.promotionService = promotionService;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.rbacService = rbacService;
        this.auditLogService = auditLogService;
    }

    public PromotionResponseDto submitPromotion(Long sourceSkillId,
                                                Long sourceVersionId,
                                                Long targetNamespaceId,
                                                String userId,
                                                Map<Long, NamespaceRole> userNsRoles,
                                                AuditRequestContext auditContext) {
        PromotionRequest promotion = promotionService.submitPromotion(
                sourceSkillId,
                sourceVersionId,
                targetNamespaceId,
                userId,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        recordAudit(
                "PROMOTION_SUBMIT",
                userId,
                promotion.getId(),
                auditContext,
                "{\"sourceSkillId\":" + sourceSkillId + ",\"sourceVersionId\":" + sourceVersionId + "}"
        );
        return toResponse(promotion);
    }

    public PromotionResponseDto approvePromotion(Long promotionId,
                                                 String comment,
                                                 String userId,
                                                 AuditRequestContext auditContext) {
        PromotionRequest promotion = promotionService.approvePromotion(
                promotionId,
                userId,
                comment,
                platformRoles(userId)
        );
        recordAudit("PROMOTION_APPROVE", userId, promotion.getId(), auditContext, detailWithComment(comment));
        return toResponse(promotion);
    }

    public PromotionResponseDto rejectPromotion(Long promotionId,
                                                String comment,
                                                String userId,
                                                AuditRequestContext auditContext) {
        PromotionRequest promotion = promotionService.rejectPromotion(
                promotionId,
                userId,
                comment,
                platformRoles(userId)
        );
        recordAudit("PROMOTION_REJECT", userId, promotion.getId(), auditContext, detailWithComment(comment));
        return toResponse(promotion);
    }

    public PageResponse<PromotionResponseDto> listPromotions(String status,
                                                             int page,
                                                             int size,
                                                             String userId) {
        requirePromotionAdmin(userId);
        ReviewTaskStatus reviewStatus = ReviewTaskStatus.valueOf(status.toUpperCase());
        Page<PromotionRequest> requests = promotionRequestRepository.findByStatus(reviewStatus, PageRequest.of(page, size));
        return PageResponse.from(requests.map(this::toResponse));
    }

    public PageResponse<PromotionResponseDto> listPendingPromotions(int page, int size, String userId) {
        requirePromotionAdmin(userId);
        Page<PromotionRequest> requests = promotionRequestRepository.findByStatus(
                ReviewTaskStatus.PENDING, PageRequest.of(page, size));
        return PageResponse.from(requests.map(this::toResponse));
    }

    public PromotionResponseDto getPromotionDetail(Long promotionId, String userId) {
        PromotionRequest promotion = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));
        if (!promotionService.canViewPromotion(promotion, userId, platformRoles(userId))) {
            throw new DomainForbiddenException("promotion.no_permission");
        }
        return toResponse(promotion);
    }

    private PromotionResponseDto toResponse(PromotionRequest request) {
        Skill sourceSkill = skillRepository.findById(request.getSourceSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", request.getSourceSkillId()));
        SkillVersion sourceVersion = skillVersionRepository.findById(request.getSourceVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", request.getSourceVersionId()));
        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        Namespace targetNamespace = namespaceRepository.findById(request.getTargetNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", request.getTargetNamespaceId()));

        String submittedByName = userAccountRepository.findById(request.getSubmittedBy())
                .map(UserAccount::getDisplayName)
                .orElse(null);
        String reviewedByName = request.getReviewedBy() != null
                ? userAccountRepository.findById(request.getReviewedBy()).map(UserAccount::getDisplayName).orElse(null)
                : null;

        return new PromotionResponseDto(
                request.getId(),
                request.getSourceSkillId(),
                sourceNamespace.getSlug(),
                sourceSkill.getSlug(),
                sourceVersion.getVersion(),
                targetNamespace.getSlug(),
                request.getTargetSkillId(),
                request.getStatus().name(),
                request.getSubmittedBy(),
                submittedByName,
                request.getReviewedBy(),
                reviewedByName,
                request.getReviewComment(),
                request.getSubmittedAt(),
                request.getReviewedAt()
        );
    }

    private void requirePromotionAdmin(String userId) {
        Set<String> platformRoles = platformRoles(userId);
        if (!platformRoles.contains("SKILL_ADMIN") && !platformRoles.contains("SUPER_ADMIN")) {
            throw new DomainForbiddenException("promotion.no_permission");
        }
    }

    private Set<String> platformRoles(String userId) {
        return rbacService.getUserRoleCodes(userId);
    }

    private Map<Long, NamespaceRole> normalizeRoles(Map<Long, NamespaceRole> userNsRoles) {
        return userNsRoles != null ? userNsRoles : Map.of();
    }

    private void recordAudit(String action,
                             String userId,
                             Long targetId,
                             AuditRequestContext auditContext,
                             String detailJson) {
        auditLogService.record(
                userId,
                action,
                "PROMOTION_REQUEST",
                targetId,
                MDC.get("requestId"),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }

    private String detailWithComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return "{\"comment\":\"" + comment.replace("\"", "\\\"") + "\"}";
    }
}
