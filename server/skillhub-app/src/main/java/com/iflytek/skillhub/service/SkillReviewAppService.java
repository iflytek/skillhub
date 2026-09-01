package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingService;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.SkillReviewMeResponse;
import com.iflytek.skillhub.dto.SkillReviewResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.SkillReviewQueryRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillReviewAppService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final VisibilityChecker visibilityChecker;
    private final SkillRatingService ratingService;
    private final SkillReviewQueryRepository queryRepository;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public SkillReviewAppService(SkillRepository skillRepository,
                                 SkillVersionRepository skillVersionRepository,
                                 VisibilityChecker visibilityChecker,
                                 SkillRatingService ratingService,
                                 SkillReviewQueryRepository queryRepository,
                                 AuditLogService auditLogService,
                                 RequestIdAccessor requestIdAccessor) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.visibilityChecker = visibilityChecker;
        this.ratingService = ratingService;
        this.queryRepository = queryRepository;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    public PageResponse<SkillReviewResponse> list(Long skillId,
                                                   String viewerId,
                                                   Map<Long, NamespaceRole> namespaceRoles,
                                                   Set<String> platformRoles,
                                                   int page,
                                                   int size) {
        requireVisibleSkill(skillId, viewerId, namespaceRoles, platformRoles);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainBadRequestException("error.pagination.invalid", MAX_PAGE_SIZE);
        }
        boolean includeHidden = isReviewModerator(platformRoles);
        return PageResponse.from(queryRepository.list(
                skillId,
                viewerId,
                includeHidden,
                PageRequest.of(page, size)
        ));
    }

    public SkillReviewMeResponse getMine(Long skillId,
                                         String userId,
                                         Map<Long, NamespaceRole> namespaceRoles,
                                         Set<String> platformRoles) {
        return ratingService.getUserFeedback(skillId, userId)
                .map(this::toMine)
                .orElseGet(SkillReviewMeResponse::empty);
    }

    public SkillReviewMeResponse upsert(Long skillId,
                                        String userId,
                                        short score,
                                        String reviewText,
                                        Map<Long, NamespaceRole> namespaceRoles,
                                        Set<String> platformRoles) {
        requireInteractableSkill(skillId, userId, namespaceRoles, platformRoles);
        return toMine(ratingService.upsertReview(skillId, userId, score, reviewText));
    }

    public SkillReviewMeResponse clear(Long skillId,
                                       String userId,
                                       Map<Long, NamespaceRole> namespaceRoles,
                                       Set<String> platformRoles) {
        return toMine(ratingService.clearReview(skillId, userId));
    }

    @Transactional
    public SkillReviewResponse hide(Long reviewId,
                                    String moderatorId,
                                    String reason,
                                    AuditRequestContext auditContext) {
        SkillRating review = ratingService.hideReview(reviewId, moderatorId, reason);
        recordModerationAudit("SKILL_REVIEW_HIDE", review, moderatorId, reason, auditContext);
        return toModerationResponse(review);
    }

    @Transactional
    public SkillReviewResponse restore(Long reviewId,
                                       String moderatorId,
                                       AuditRequestContext auditContext) {
        SkillRating review = ratingService.restoreReview(reviewId, moderatorId);
        recordModerationAudit("SKILL_REVIEW_RESTORE", review, moderatorId, null, auditContext);
        return toModerationResponse(review);
    }

    private Skill requireVisibleSkill(Long skillId,
                                      String userId,
                                      Map<Long, NamespaceRole> namespaceRoles,
                                      Set<String> platformRoles) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", skillId));
        if (!visibilityChecker.canAccess(
                skill,
                userId,
                namespaceRoles != null ? namespaceRoles : Map.of(),
                platformRoles != null ? platformRoles : Set.of())) {
            throw new DomainForbiddenException("error.skill.access.denied", skill.getSlug());
        }
        return skill;
    }

    private Skill requireInteractableSkill(Long skillId,
                                           String userId,
                                           Map<Long, NamespaceRole> namespaceRoles,
                                           Set<String> platformRoles) {
        Skill skill = requireVisibleSkill(skillId, userId, namespaceRoles, platformRoles);
        boolean published = skill.getLatestVersionId() != null
                && skillVersionRepository.findById(skill.getLatestVersionId())
                .map(version -> version.getStatus() == SkillVersionStatus.PUBLISHED)
                .orElse(false);
        if (skill.getStatus() != SkillStatus.ACTIVE || !published) {
            throw new DomainBadRequestException("error.skillReview.notInteractable");
        }
        return skill;
    }

    private SkillReviewMeResponse toMine(SkillRating review) {
        return new SkillReviewMeResponse(
                true,
                review.getScore(),
                review.hasReview(),
                review.hasReview() ? review.getId() : null,
                review.getReviewText(),
                review.hasReview() ? review.getReviewStatus().name() : null,
                review.getModerationReason(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private SkillReviewResponse toModerationResponse(SkillRating review) {
        return new SkillReviewResponse(
                review.getId(),
                review.getUserId(),
                review.getUserId(),
                null,
                review.getScore(),
                review.getReviewText(),
                review.getReviewStatus().name(),
                false,
                review.getModerationReason(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private boolean isReviewModerator(Set<String> platformRoles) {
        return platformRoles != null
                && (platformRoles.contains("SKILL_ADMIN") || platformRoles.contains("SUPER_ADMIN"));
    }

    private void recordModerationAudit(String action,
                                       SkillRating review,
                                       String moderatorId,
                                       String reason,
                                       AuditRequestContext context) {
        auditLogService.record(
                moderatorId,
                action,
                "SKILL_REVIEW",
                review.getId(),
                requestIdAccessor.current(),
                context != null ? context.clientIp() : null,
                context != null ? context.userAgent() : null,
                AuditDetail.builder()
                        .put("skillId", review.getSkillId())
                        .put("reason", reason)
                        .build()
        );
    }
}
