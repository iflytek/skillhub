package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.device.DeviceCodeResponse;
import com.iflytek.skillhub.auth.device.DeviceTokenResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceAuthControllerTest {

    private final DeviceAuthService deviceAuthService = mock(DeviceAuthService.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final Clock clock = mock(Clock.class);
    private final ApiResponseFactory responseFactory = new ApiResponseFactory(messageSource, clock);

    private final DeviceAuthController controller = new DeviceAuthController(responseFactory, deviceAuthService);

    @Test
    void requestDeviceCode_returnsOkResponse() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("ok");

        DeviceCodeResponse response = new DeviceCodeResponse(
                "dc-123", "uc-456", "https://example.com/verify", 600, 5
        );
        when(deviceAuthService.generateDeviceCode()).thenReturn(response);

        var result = controller.requestDeviceCode();

        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo(0);
        assertThat(result.data()).isEqualTo(response);
    }

    @Test
    void pollToken_returnsOkResponse() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("ok");

        DeviceTokenResponse response = DeviceTokenResponse.success("token-123");
        when(deviceAuthService.pollToken("dc-123")).thenReturn(response);

        var result = controller.pollToken(new DeviceAuthController.TokenRequest("dc-123"));

        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo(0);
        assertThat(result.data()).isEqualTo(response);
    }

    @Test
    void tokenRequest_recordCreated() {
        var request = new DeviceAuthController.TokenRequest("dc-789");
        assertThat(request.deviceCode()).isEqualTo("dc-789");
    }
}
