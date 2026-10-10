package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionService;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPortalAppServiceTest {

    private static final Long PROMOTION_ID = 1L;
    private static final String SUPER_ADMIN_ID = "super-admin";
    private static final String REVIEWER_ID = "reviewer";
    private static final String SUBMITTER_ID = "submitter";

    @Mock
    private PromotionService promotionService;
    @Mock
    private PromotionRequestRepository promotionRequestRepository;
    @Mock
    private GovernanceQueryRepository governanceQueryRepository;
    @Mock
    private RbacService rbacService;
    @Mock
    private AuditLogService auditLogService;

    private PromotionPortalAppService service;

    @BeforeEach
    void setUp() {
        service = new PromotionPortalAppService(
                promotionService,
                promotionRequestRepository,
                governanceQueryRepository,
                rbacService,
                auditLogService,
                new RequestIdAccessor()
        );
    }

    @Test
    void approvePromotion_recordsSelfReviewAuditDetailForSuperAdminSelfApproval() {
        PromotionRequest promotion = promotionRequest(PROMOTION_ID, SUPER_ADMIN_ID);
        when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
        when(rbacService.getUserRoleCodes(SUPER_ADMIN_ID)).thenReturn(Set.of("SUPER_ADMIN"));
        when(promotionService.approvePromotion(PROMOTION_ID, SUPER_ADMIN_ID, "ship", Set.of("SUPER_ADMIN"), Set.of("SUPER_ADMIN")))
                .thenReturn(promotion);
        when(governanceQueryRepository.getPromotionResponse(promotion)).thenReturn(response(promotion));

        service.approvePromotion(
                PROMOTION_ID,
                "ship",
                SUPER_ADMIN_ID,
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        verify(auditLogService).record(
                eq(SUPER_ADMIN_ID),
                eq("PROMOTION_APPROVE"),
                eq("PROMOTION_REQUEST"),
                eq(PROMOTION_ID),
                eq(null),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq("{\"comment\":\"ship\",\"selfReview\":true}")
        );
    }

    @Test
    void rejectPromotion_recordsSelfReviewAuditDetailWithoutComment() {
        PromotionRequest promotion = promotionRequest(PROMOTION_ID, SUPER_ADMIN_ID);
        when(rbacService.getUserRoleCodes(SUPER_ADMIN_ID)).thenReturn(Set.of("SUPER_ADMIN"));
        when(promotionService.rejectPromotion(PROMOTION_ID, SUPER_ADMIN_ID, null, Set.of("SUPER_ADMIN")))
                .thenReturn(promotion);
        when(governanceQueryRepository.getPromotionResponse(promotion)).thenReturn(response(promotion));

        service.rejectPromotion(
                PROMOTION_ID,
                null,
                SUPER_ADMIN_ID,
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        verify(auditLogService).record(
                eq(SUPER_ADMIN_ID),
                eq("PROMOTION_REJECT"),
                eq("PROMOTION_REQUEST"),
                eq(PROMOTION_ID),
                eq(null),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq("{\"selfReview\":true}")
        );
    }

    @Test
    void approvePromotion_keepsExistingAuditDetailForReviewerApprovingOthersPromotion() {
        PromotionRequest promotion = promotionRequest(PROMOTION_ID, SUBMITTER_ID);
        when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
        when(rbacService.getUserRoleCodes(REVIEWER_ID)).thenReturn(Set.of("SKILL_ADMIN"));
        when(rbacService.getUserRoleCodes(SUBMITTER_ID)).thenReturn(Set.of());
        when(promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ship", Set.of("SKILL_ADMIN"), Set.of()))
                .thenReturn(promotion);
        when(governanceQueryRepository.getPromotionResponse(promotion)).thenReturn(response(promotion));

        service.approvePromotion(
                PROMOTION_ID,
                "ship",
                REVIEWER_ID,
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        verify(auditLogService).record(
                eq(REVIEWER_ID),
                eq("PROMOTION_APPROVE"),
                eq("PROMOTION_REQUEST"),
                eq(PROMOTION_ID),
                eq(null),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq("{\"comment\":\"ship\"}")
        );
    }

    @Test
    void approvePromotion_escapesMultiLineReviewCommentIntoValidJson() {
        // Regression: the detail column is JSONB. A reviewer pressing Enter used
        // to produce a raw newline inside the JSON string, so the audit insert
        // failed after the promotion had already been approved.
        String comment = "looks good\nbut rename it \"foo\"\tfirst\\done";
        PromotionRequest promotion = promotionRequest(PROMOTION_ID, SUBMITTER_ID);
        when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
        when(rbacService.getUserRoleCodes(REVIEWER_ID)).thenReturn(Set.of("SKILL_ADMIN"));
        when(rbacService.getUserRoleCodes(SUBMITTER_ID)).thenReturn(Set.of());
        when(promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, comment, Set.of("SKILL_ADMIN"), Set.of()))
                .thenReturn(promotion);
        when(governanceQueryRepository.getPromotionResponse(promotion)).thenReturn(response(promotion));

        service.approvePromotion(
                PROMOTION_ID,
                comment,
                REVIEWER_ID,
                new AuditRequestContext("127.0.0.1", "JUnit")
        );

        verify(auditLogService).record(
                eq(REVIEWER_ID),
                eq("PROMOTION_APPROVE"),
                eq("PROMOTION_REQUEST"),
                eq(PROMOTION_ID),
                eq(null),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq("{\"comment\":\"looks good\\nbut rename it \\\"foo\\\"\\tfirst\\\\done\"}")
        );
    }

    private PromotionResponseDto response(PromotionRequest request) {
        return new PromotionResponseDto(
                request.getId(),
                request.getSourceSkillId(),
                "Skill A",
                "Skill A summary",
                "team-a",
                "skill-a",
                "1.0.0",
                3,
                2048L,
                7L,
                2,
                "global",
                request.getTargetSkillId(),
                request.getStatus().name(),
                request.getSubmittedBy(),
                "Submitter",
                request.getReviewedBy(),
                null,
                request.getReviewComment(),
                request.getSubmittedAt(),
                request.getReviewedAt()
        );
    }

    private PromotionRequest promotionRequest(Long id, String submittedBy) {
        PromotionRequest request = new PromotionRequest(10L, 20L, 30L, submittedBy);
        setId(request, id);
        return request;
    }

    private void setId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
