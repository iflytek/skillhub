package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionService;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PromotionPortalAppService {

    private final PromotionService promotionService;
    private final PromotionRequestRepository promotionRequestRepository;
    private final GovernanceQueryRepository governanceQueryRepository;
    private final RbacService rbacService;
    private final AuditLogService auditLogService;

    public PromotionPortalAppService(PromotionService promotionService,
                                     PromotionRequestRepository promotionRequestRepository,
                                     GovernanceQueryRepository governanceQueryRepository,
                                     RbacService rbacService,
                                     AuditLogService auditLogService) {
        this.promotionService = promotionService;
        this.promotionRequestRepository = promotionRequestRepository;
        this.governanceQueryRepository = governanceQueryRepository;
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
        return governanceQueryRepository.getPromotionResponse(promotion);
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
        return governanceQueryRepository.getPromotionResponse(promotion);
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
        return governanceQueryRepository.getPromotionResponse(promotion);
    }

    public PageResponse<PromotionResponseDto> listPromotions(String status,
                                                             int page,
                                                             int size,
                                                             String userId) {
        requirePromotionAdmin(userId);
        ReviewTaskStatus reviewStatus = ReviewTaskStatus.valueOf(status.toUpperCase());
        Page<PromotionRequest> requests = promotionRequestRepository.findByStatus(reviewStatus, PageRequest.of(page, size));
        return PageResponse.from(new PageImpl<>(
                governanceQueryRepository.getPromotionResponses(requests.getContent()),
                requests.getPageable(),
                requests.getTotalElements()
        ));
    }

    public PageResponse<PromotionResponseDto> listPendingPromotions(int page, int size, String userId) {
        requirePromotionAdmin(userId);
        Page<PromotionRequest> requests = promotionRequestRepository.findByStatus(
                ReviewTaskStatus.PENDING, PageRequest.of(page, size));
        return PageResponse.from(new PageImpl<>(
                governanceQueryRepository.getPromotionResponses(requests.getContent()),
                requests.getPageable(),
                requests.getTotalElements()
        ));
    }

    public PromotionResponseDto getPromotionDetail(Long promotionId, String userId) {
        PromotionRequest promotion = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));
        if (!promotionService.canViewPromotion(promotion, userId, platformRoles(userId))) {
            throw new DomainForbiddenException("promotion.no_permission");
        }
        return governanceQueryRepository.getPromotionResponse(promotion);
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
