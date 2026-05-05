package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.PromotionService;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.repository.GovernanceQueryRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PromotionPortalAppServiceTest {

    private final PromotionService promotionService = mock(PromotionService.class);
    private final PromotionRequestRepository promotionRequestRepository = mock(PromotionRequestRepository.class);
    private final GovernanceQueryRepository governanceQueryRepository = mock(GovernanceQueryRepository.class);
    private final RbacService rbacService = mock(RbacService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final PromotionPortalAppService service = new PromotionPortalAppService(
            promotionService,
            promotionRequestRepository,
            governanceQueryRepository,
            rbacService,
            auditLogService
    );

    @Test
    void approvePromotion_delegatesToDomainServiceAndRecordsAudit() {
        PromotionRequest promotion = createPromotionRequest(1L, "user-1");
        when(rbacService.getUserRoleCodes("admin")).thenReturn(Set.of("SKILL_ADMIN"));
        when(promotionService.approvePromotion(1L, "admin", "approved", Set.of("SKILL_ADMIN")))
                .thenReturn(promotion);
        when(governanceQueryRepository.getPromotionResponse(promotion))
                .thenReturn(toPromotionResponse(promotion));

        PromotionResponseDto response = service.approvePromotion(1L, "approved", "admin", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getPromotionDetail_throwsWhenNotFound() {
        when(promotionRequestRepository.findById(99L)).thenReturn(Optional.empty());
        when(rbacService.getUserRoleCodes("user-1")).thenReturn(Set.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getPromotionDetail(99L, "user-1"))
                .isInstanceOf(com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException.class)
                .hasMessageContaining("promotion.not_found");
    }

    private PromotionRequest createPromotionRequest(Long id, String submittedBy) {
        PromotionRequest request = new PromotionRequest(10L, 20L, 30L, submittedBy);
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "status", ReviewTaskStatus.PENDING);
        return request;
    }

    private PromotionResponseDto toPromotionResponse(PromotionRequest request) {
        return new PromotionResponseDto(
                request.getId(),
                request.getSourceSkillId(),
                "team-a",
                "skill-a",
                "1.0.0",
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
}
