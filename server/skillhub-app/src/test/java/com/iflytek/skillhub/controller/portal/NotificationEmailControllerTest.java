package com.iflytek.skillhub.controller.portal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.ratelimit.*;
import com.iflytek.skillhub.service.NotificationEmailContactService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class NotificationEmailControllerTest {
    @Test
    void sourceRateLimitIsIndependentOfAccountAndBlocksBeforeSend() {
        var service = mock(NotificationEmailContactService.class);
        var limiter = mock(RateLimiter.class);
        var ips = mock(ClientIpResolver.class);
        var request = new MockHttpServletRequest();
        when(ips.resolve(request)).thenReturn("192.0.2.1");
        var controller = new NotificationEmailController(service, mock(ApiResponseFactory.class), limiter, ips);
        assertThatThrownBy(
                () -> controller.requestCode("user", new NotificationEmailController.CodeRequest("en"), request))
                .isInstanceOf(com.iflytek.skillhub.auth.exception.AuthFlowException.class);
        verify(limiter).tryAcquire("ratelimit:notification-email-code:source:192.0.2.1", 30, 3600);
        verifyNoInteractions(service);
    }
    @Test
    void requestAlwaysUsesAuthenticatedUserId() {
        var service = mock(NotificationEmailContactService.class);
        var limiter = mock(RateLimiter.class);
        var ips = mock(ClientIpResolver.class);
        var request = new MockHttpServletRequest();
        when(ips.resolve(request)).thenReturn("192.0.2.1");
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
        var controller = new NotificationEmailController(service, mock(ApiResponseFactory.class), limiter, ips);
        controller.requestCode("authenticated-user", new NotificationEmailController.CodeRequest("zh"), request);
        verify(service).requestCode("authenticated-user", "zh");
    }
}
