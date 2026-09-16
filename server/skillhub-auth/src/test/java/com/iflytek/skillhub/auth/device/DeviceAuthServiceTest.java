package com.iflytek.skillhub.auth.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAuthServiceTest {

    private static final String DEVICE_CODE = "device-code-1";
    private static final String USER_CODE = "ABCD-2345";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ApiTokenService apiTokenService;

    private DeviceAuthService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DeviceAuthService(redisTemplate, apiTokenService, new ObjectMapper(), "/cli/auth");
    }

    /**
     * The shared RedisTemplate's JSON serializer keeps no type information, so
     * stored DeviceCodeData comes back as a plain map. A typed cast used to
     * throw ClassCastException on every poll; the service must convert instead.
     */
    private static Map<String, Object> storedDeviceCode(DeviceCodeStatus status, String userId) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("deviceCode", DEVICE_CODE);
        raw.put("userCode", USER_CODE);
        raw.put("status", status.name());
        raw.put("userId", userId);
        return raw;
    }

    @Test
    void pollTokenReturnsPendingWhenRedisValueIsUntypedMap() {
        when(valueOperations.get("device:code:" + DEVICE_CODE))
            .thenReturn(storedDeviceCode(DeviceCodeStatus.PENDING, null));

        DeviceTokenResponse response = service.pollToken(DEVICE_CODE);

        assertThat(response.error()).isEqualTo("authorization_pending");
    }

    @Test
    void pollTokenRedeemsAuthorizedCodeFromUntypedMap() {
        when(valueOperations.get("device:code:" + DEVICE_CODE))
            .thenReturn(storedDeviceCode(DeviceCodeStatus.AUTHORIZED, "usr_1"));
        when(valueOperations.setIfAbsent(eq("device:claim:" + DEVICE_CODE), any(), anyLong(), any()))
            .thenReturn(Boolean.TRUE);
        when(apiTokenService.rotateToken(eq("usr_1"), any(), any()))
            .thenReturn(new ApiTokenService.TokenCreateResult("sk_test_token", null));

        DeviceTokenResponse response = service.pollToken(DEVICE_CODE);

        assertThat(response.accessToken()).isEqualTo("sk_test_token");
        verify(apiTokenService).rotateToken(
            "usr_1",
            "CLI Device Flow",
            "[\"skill:read\",\"skill:publish\",\"skill:delete\"]"
        );
    }

    @Test
    void pollTokenRejectsUnknownDeviceCode() {
        when(valueOperations.get("device:code:" + DEVICE_CODE)).thenReturn(null);

        assertThatThrownBy(() -> service.pollToken(DEVICE_CODE))
            .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void authorizeDeviceCodeMarksPendingCodeFromUntypedMap() {
        when(valueOperations.get("device:usercode:" + USER_CODE)).thenReturn(DEVICE_CODE);
        when(valueOperations.get("device:code:" + DEVICE_CODE))
            .thenReturn(storedDeviceCode(DeviceCodeStatus.PENDING, null));

        service.authorizeDeviceCode(USER_CODE, "usr_1");

        verify(valueOperations).set(startsWith("device:code:"), any(DeviceCodeData.class), anyLong(), any());
    }
}
