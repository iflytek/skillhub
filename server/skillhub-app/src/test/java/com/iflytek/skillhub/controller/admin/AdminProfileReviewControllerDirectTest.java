package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.domain.user.ProfileReviewService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.service.AdminProfileReviewAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminProfileReviewControllerDirectTest {

    private final AdminProfileReviewAppService appService = mock(AdminProfileReviewAppService.class);
    private final ProfileReviewService reviewService = mock(ProfileReviewService.class);
    private final ApiResponseFactory responseFactory = mock(ApiResponseFactory.class);

    private final AdminProfileReviewController controller = new AdminProfileReviewController(
            responseFactory, appService, reviewService
    );

    @Test
    void resolveClientIp_withXForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "resolveClientIp", request);
        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    void resolveClientIp_withUnknownForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.3");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "resolveClientIp", request);
        assertThat(ip).isEqualTo("10.0.0.3");
    }

    @Test
    void resolveClientIp_withRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "resolveClientIp", request);
        assertThat(ip).isEqualTo("192.168.1.1");
    }
}
