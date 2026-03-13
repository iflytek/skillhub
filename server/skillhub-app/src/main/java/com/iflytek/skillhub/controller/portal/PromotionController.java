package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.controller.BaseApiController;
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
import com.iflytek.skillhub.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController extends BaseApiController {

    private final PromotionService promotionService;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final UserAccountRepository userAccountRepository;
    private final RbacService rbacService;

    public PromotionController(PromotionService promotionService,
                               PromotionRequestRepository promotionRequestRepository,
                               SkillRepository skillRepository,
                               SkillVersionRepository skillVersionRepository,
                               NamespaceRepository namespaceRepository,
                               UserAccountRepository userAccountRepository,
                               RbacService rbacService,
                               ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.promotionService = promotionService;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.userAccountRepository = userAccountRepository;
        this.rbacService = rbacService;
    }

    @PostMapping
    public ApiResponse<PromotionResponseDto> submitPromotion(
            @RequestBody PromotionRequestDto request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        PromotionRequest promotion = promotionService.submitPromotion(
                request.sourceSkillId(), request.sourceVersionId(),
                request.targetNamespaceId(), userId,
                userNsRoles != null ? userNsRoles : Map.of(),
                rbacService.getUserRoleCodes(userId));
        return ok("response.success.created", toResponse(promotion));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<PromotionResponseDto> approvePromotion(
            @PathVariable Long id,
            @RequestBody(required = false) PromotionActionRequest request,
            @RequestAttribute("userId") String userId) {
        String comment = request != null ? request.comment() : null;
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        PromotionRequest promotion = promotionService.approvePromotion(id, userId, comment, platformRoles);
        return ok("response.success.updated", toResponse(promotion));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<PromotionResponseDto> rejectPromotion(
            @PathVariable Long id,
            @RequestBody(required = false) PromotionActionRequest request,
            @RequestAttribute("userId") String userId) {
        String comment = request != null ? request.comment() : null;
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        PromotionRequest promotion = promotionService.rejectPromotion(id, userId, comment, platformRoles);
        return ok("response.success.updated", toResponse(promotion));
    }

    @GetMapping("/pending")
    public ApiResponse<PageResponse<PromotionResponseDto>> listPendingPromotions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute("userId") String userId) {
        Set<String> platformRoles = rbacService.getUserRoleCodes(userId);
        boolean hasAdminRole = platformRoles.contains("SKILL_ADMIN") || platformRoles.contains("SUPER_ADMIN");
        if (!hasAdminRole) {
            throw new DomainForbiddenException("promotion.no_permission");
        }
        Page<PromotionRequest> requests = promotionRequestRepository.findByStatus(
                ReviewTaskStatus.PENDING, PageRequest.of(page, size));
        return ok("response.success.read", PageResponse.from(requests.map(this::toResponse)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionResponseDto> getPromotionDetail(@PathVariable Long id,
                                                                @RequestAttribute("userId") String userId) {
        PromotionRequest promotion = promotionRequestRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", id));
        if (!promotionService.canViewPromotion(promotion, userId, rbacService.getUserRoleCodes(userId))) {
            throw new DomainForbiddenException("promotion.no_permission");
        }
        return ok("response.success.read", toResponse(promotion));
    }

    private PromotionResponseDto toResponse(PromotionRequest req) {
        Skill sourceSkill = skillRepository.findById(req.getSourceSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", req.getSourceSkillId()));
        SkillVersion sourceVersion = skillVersionRepository.findById(req.getSourceVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", req.getSourceVersionId()));
        Namespace sourceNs = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        Namespace targetNs = namespaceRepository.findById(req.getTargetNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", req.getTargetNamespaceId()));

        String submittedByName = userAccountRepository.findById(req.getSubmittedBy())
                .map(UserAccount::getDisplayName).orElse(null);

        String reviewedByName = req.getReviewedBy() != null
                ? userAccountRepository.findById(req.getReviewedBy())
                        .map(UserAccount::getDisplayName).orElse(null)
                : null;

        return new PromotionResponseDto(
                req.getId(),
                req.getSourceSkillId(),
                sourceNs.getSlug(),
                sourceSkill.getSlug(),
                sourceVersion.getVersion(),
                targetNs.getSlug(),
                req.getTargetSkillId(),
                req.getStatus().name(),
                req.getSubmittedBy(),
                submittedByName,
                req.getReviewedBy(),
                reviewedByName,
                req.getReviewComment(),
                req.getSubmittedAt(),
                req.getReviewedAt()
        );
    }
}
