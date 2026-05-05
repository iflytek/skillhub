package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.time.Clock;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeviceAuthWebControllerTest {

    private final DeviceAuthService deviceAuthService = mock(DeviceAuthService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final Clock clock = mock(Clock.class);
    private final ApiResponseFactory responseFactory = new ApiResponseFactory(messageSource, clock);

    private final DeviceAuthWebController controller = new DeviceAuthWebController(
            responseFactory, deviceAuthService, auditLogService
    );

    @Test
    void authorizeDevice_recordsAuditAndReturnsOk() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("ok");

        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "", "github", Set.of("USER")
        );
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        var result = controller.authorizeDevice(
                new DeviceAuthWebController.AuthorizeRequest("uc-123"), principal, httpRequest
        );

        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo(0);

        verify(deviceAuthService).authorizeDeviceCode("uc-123", "user-42");
        verify(auditLogService).record(
                eq("user-42"),
                eq("DEVICE_AUTHORIZE"),
                eq("DEVICE_CODE"),
                isNull(),
                any(),
                eq("127.0.0.1"),
                eq("Mozilla/5.0"),
                eq("{\"userCode\":\"uc-123\"}")
        );
    }

    @Test
    void authorizeRequest_recordCreated() {
        var request = new DeviceAuthWebController.AuthorizeRequest("uc-456");
        assertThat(request.userCode()).isEqualTo("uc-456");
    }
}
