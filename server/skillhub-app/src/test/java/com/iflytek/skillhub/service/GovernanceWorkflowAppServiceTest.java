package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceResponse;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GovernanceWorkflowAppServiceTest {

    private final ReviewPortalAppService reviewPortalAppService = mock(ReviewPortalAppService.class);
    private final ReviewSkillDetailAppService reviewSkillDetailAppService = mock(ReviewSkillDetailAppService.class);
    private final PromotionPortalAppService promotionPortalAppService = mock(PromotionPortalAppService.class);
    private final SkillLifecycleAppService skillLifecycleAppService = mock(SkillLifecycleAppService.class);
    private final NamespacePortalCommandAppService namespacePortalCommandAppService = mock(NamespacePortalCommandAppService.class);

    private final GovernanceWorkflowAppService service = new GovernanceWorkflowAppService(
            reviewPortalAppService,
            reviewSkillDetailAppService,
            promotionPortalAppService,
            skillLifecycleAppService,
            namespacePortalCommandAppService
    );

    @Test
    void freezeNamespace_delegatesToNamespaceCommandService() {
        NamespaceResponse expected = new NamespaceResponse(1L, "global", "Global", NamespaceStatus.ACTIVE, null, null, null, null, null, null);
        when(namespacePortalCommandAppService.freezeNamespace("global", new NamespaceLifecycleRequest("maintenance"), "admin-1", new AuditRequestContext("127.0.0.1", "JUnit")))
                .thenReturn(expected);

        NamespaceResponse result = service.freezeNamespace("global", new NamespaceLifecycleRequest("maintenance"), "admin-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void approveReview_delegatesToReviewPortalAppService() {
        ReviewTaskResponse expected = new ReviewTaskResponse(1L, 10L, "team-a", "skill-a", "1.0.0", "APPROVED", "user-1", null, null, null, null, null, null);
        AuditRequestContext auditContext = new AuditRequestContext("127.0.0.1", "JUnit");
        when(reviewPortalAppService.approveReview(1L, "looks good", "admin", Map.of(), auditContext))
                .thenReturn(expected);

        ReviewTaskResponse result = service.approveReview(1L, "looks good", "admin", Map.of(), auditContext);

        assertThat(result).isEqualTo(expected);
        verify(reviewPortalAppService).approveReview(1L, "looks good", "admin", Map.of(), auditContext);
    }

    @Test
    void approvePromotion_delegatesToPromotionPortalAppService() {
        PromotionResponseDto expected = new PromotionResponseDto(1L, 10L, "team-a", "skill-a", "1.0.0", "global", 20L, "APPROVED", "user-1", null, null, null, null, null, null);
        AuditRequestContext auditContext = new AuditRequestContext("127.0.0.1", "JUnit");
        when(promotionPortalAppService.approvePromotion(1L, "approved", "admin", auditContext))
                .thenReturn(expected);

        PromotionResponseDto result = service.approvePromotion(1L, "approved", "admin", auditContext);

        assertThat(result).isEqualTo(expected);
        verify(promotionPortalAppService).approvePromotion(1L, "approved", "admin", auditContext);
    }
}
