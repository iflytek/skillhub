package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PromotionRevocationActionRequest;
import com.iflytek.skillhub.dto.PromotionRevocationSubmitRequest;
import com.iflytek.skillhub.service.PromotionRevocationPortalAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PromotionRevocationControllerTest {
    @Mock PromotionRevocationPortalAppService appService;
    @Mock ApiResponseFactory responseFactory;
    @Mock HttpServletRequest request;

    @Test
    void submitForwardsSourceIdAndAuthenticatedUser() {
        PromotionRevocationController controller = new PromotionRevocationController(appService, responseFactory);

        controller.submit(new PromotionRevocationSubmitRequest(10L, "withdraw"), "owner-1", null, request);

        verify(appService).submit(eq(10L), eq("owner-1"), eq(null), eq("withdraw"), any());
    }

    @Test
    void approveUsesReviewerIdentityAndRequestId() {
        PromotionRevocationController controller = new PromotionRevocationController(appService, responseFactory);

        controller.approve(50L, new PromotionRevocationActionRequest("approved"), "admin-1", request);

        verify(appService).approve(eq(50L), eq("admin-1"), eq("approved"), any());
    }

    @Test
    void reviewedHistoryForwardsPaginationAndAuthenticatedUser() {
        PromotionRevocationController controller = new PromotionRevocationController(appService, responseFactory);

        controller.reviewedHistory("admin-1", 1, 25);

        verify(appService).reviewedHistory("admin-1", 1, 25);
    }
}
