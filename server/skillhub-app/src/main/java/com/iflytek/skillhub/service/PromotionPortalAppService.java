package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionService;
import com.iflytek.skillhub.domain.review.PromotionState;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionPortalAppService {

    private final PromotionService promotionService;
    private final PromotionRequestRepository promotionRequestRepository;
    private final GovernanceQueryRepository governanceQueryRepository;
    private final RbacService rbacService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public PromotionPortalAppService(PromotionService promotionService,
                                     PromotionRequestRepository promotionRequestRepository,
                                     GovernanceQueryRepository governanceQueryRepository,
                                     RbacService rbacService,
                                     AuditLogService auditLogService,
                                     RequestIdAccessor requestIdAccessor) {
        this.promotionService = promotionService;
        this.promotionRequestRepository = promotionRequestRepository;
        this.governanceQueryRepository = governanceQueryRepository;
        this.rbacService = rbacService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    @Transactional
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
                AuditDetail.builder()
                        .put("sourceSkillId", sourceSkillId)
                        .put("sourceVersionId", sourceVersionId)
                        .put("requestKind", promotion.getRequestKind().name())
                        .put("targetSkillId", promotion.getTargetSkillId())
                        .build()
        );
        return governanceQueryRepository.getPromotionResponse(promotion);
    }

    @Transactional
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
        recordAudit("PROMOTION_APPROVE", userId, promotion.getId(), auditContext,
                promotion.getRequestKind() == com.iflytek.skillhub.domain.review.PromotionRequestKind.INITIAL
                        ? detailWithComment(comment, promotion.getSubmittedBy().equals(userId))
                        : AuditDetail.builder()
                                .put("requestKind", promotion.getRequestKind().name())
                                .put("targetSkillId", promotion.getTargetSkillId())
                                .put("targetVersionId", promotion.getTargetVersionId())
                                .put("comment", comment)
                                .put("selfReview", promotion.getSubmittedBy().equals(userId) ? Boolean.TRUE : null)
                                .build());
        return governanceQueryRepository.getPromotionResponse(promotion);
    }

    @Transactional
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
        recordAudit("PROMOTION_REJECT", userId, promotion.getId(), auditContext,
                detailWithComment(comment, promotion.getSubmittedBy().equals(userId)));
        return governanceQueryRepository.getPromotionResponse(promotion);
    }

    public PageResponse<PromotionResponseDto> listPromotions(String status,
                                                             int page,
                                                             int size,
                                                             String sortBy,
                                                             String sortDirection,
                                                             String userId) {
        requirePromotionAdmin(userId);
        ReviewTaskStatus reviewStatus = parsePromotionStatus(status);
        Page<PromotionRequest> requests = findPromotionRequests(reviewStatus, page, size, sortBy, sortDirection);
        return PageResponse.from(new PageImpl<>(
                governanceQueryRepository.getPromotionResponses(requests.getContent()),
                requests.getPageable(),
                requests.getTotalElements()
        ));
    }

    public PageResponse<PromotionResponseDto> listPendingPromotions(int page, int size, String userId) {
        requirePromotionAdmin(userId);
        Page<PromotionRequest> requests = promotionRequestRepository.findByStatus(
                ReviewTaskStatus.PENDING,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                new Sort.Order(Sort.Direction.DESC, "submittedAt"),
                                new Sort.Order(Sort.Direction.DESC, "id")
                        )
                )
        );
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

    public PromotionState getSourceState(Long sourceSkillId, String userId,
                                         Map<Long, NamespaceRole> userNsRoles) {
        return promotionService.getSourceState(sourceSkillId, userId, normalizeRoles(userNsRoles),
                platformRoles(userId));
    }

    private ReviewTaskStatus parsePromotionStatus(String status) {
        if (status == null) {
            return ReviewTaskStatus.PENDING;
        }
        if (status.isBlank()) {
            throw new DomainBadRequestException("promotion.status.invalid", status);
        }
        try {
            ReviewTaskStatus parsed = ReviewTaskStatus.valueOf(status.toUpperCase(Locale.ROOT));
            return switch (parsed) {
                case PENDING, APPROVED, REJECTED -> parsed;
                default -> throw new DomainBadRequestException("promotion.status.invalid", status);
            };
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("promotion.status.invalid", status);
        }
    }

    private Page<PromotionRequest> findPromotionRequests(ReviewTaskStatus status,
                                                         int page,
                                                         int size,
                                                         String sortBy,
                                                         String sortDirection) {
        if (status == ReviewTaskStatus.PENDING) {
            if (sortBy != null || sortDirection != null) {
                throw new DomainBadRequestException("promotion.sort.pending_unsupported");
            }
            return promotionRequestRepository.findByStatus(
                    status,
                    PageRequest.of(
                            page,
                            size,
                            Sort.by(
                                    new Sort.Order(Sort.Direction.DESC, "submittedAt"),
                                    new Sort.Order(Sort.Direction.DESC, "id")
                            )
                    )
            );
        }

        if (sortBy != null && (sortBy.isBlank() || !"reviewedAt".equals(sortBy))) {
            throw new DomainBadRequestException("promotion.sort.field.invalid", sortBy);
        }

        Sort.Direction direction = parsePromotionSortDirection(sortDirection);
        Pageable pageable = PageRequest.of(page, size);
        if (direction == Sort.Direction.ASC) {
            return promotionRequestRepository.findHistoryByStatusOrderByReviewedAtAsc(status, pageable);
        }
        return promotionRequestRepository.findHistoryByStatusOrderByReviewedAtDesc(status, pageable);
    }

    private Sort.Direction parsePromotionSortDirection(String sortDirection) {
        if (sortDirection == null) {
            return Sort.Direction.DESC;
        }
        if (sortDirection.isBlank()) {
            throw new DomainBadRequestException("promotion.sort.direction.invalid", sortDirection);
        }
        try {
            return Sort.Direction.valueOf(sortDirection.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("promotion.sort.direction.invalid", sortDirection);
        }
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
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }

    private String detailWithComment(String comment, boolean selfReview) {
        boolean hasComment = comment != null && !comment.isBlank();
        return AuditDetail.builder()
                .put("comment", hasComment ? comment : null)
                .put("selfReview", selfReview ? Boolean.TRUE : null)
                .build();
    }
}
