package com.iflytek.skillhub.auth.device;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceAuthServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private RedisOperations<String, Object> redisOperations;

    @Mock
    private ApiTokenService apiTokenService;

    private DeviceAuthService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisOperations.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.execute(any(SessionCallback.class)))
                .thenAnswer(invocation -> invocation.<SessionCallback<?>>getArgument(0).execute(redisOperations));
        service = new DeviceAuthService(redisTemplate, apiTokenService, "https://skillhub.example.com/device", new ObjectMapper());
    }

    @Test
    void generateDeviceCode_returns_valid_response() {
        // When
        DeviceCodeResponse response = service.generateDeviceCode();

        // Then
        assertThat(response.deviceCode()).isNotEmpty();
        assertThat(response.userCode()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(response.verificationUri()).isEqualTo("https://skillhub.example.com/device");
        assertThat(response.expiresIn()).isEqualTo(900); // 15 minutes
        assertThat(response.interval()).isEqualTo(5);

        // Verify Redis storage
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations, times(2)).set(keyCaptor.capture(), valueCaptor.capture(), eq(15L), eq(TimeUnit.MINUTES));

        // Verify device code key and data
        assertThat(keyCaptor.getAllValues().get(0)).startsWith("device:code:");
        DeviceCodeData data = (DeviceCodeData) valueCaptor.getAllValues().get(0);
        assertThat(data.getDeviceCode()).isEqualTo(response.deviceCode());
        assertThat(data.getUserCode()).isEqualTo(response.userCode());
        assertThat(data.getStatus()).isEqualTo(DeviceCodeStatus.PENDING);

        // Verify user code key and value
        assertThat(keyCaptor.getAllValues().get(1)).startsWith("device:usercode:");
        assertThat(valueCaptor.getAllValues().get(1)).isEqualTo(response.deviceCode());
    }

    @Test
    void pollToken_returns_pending_when_not_authorized() {
        // Given
        DeviceCodeData data = new DeviceCodeData("device123", "ABCD-1234", DeviceCodeStatus.PENDING, null);
        when(valueOperations.get("device:code:device123")).thenReturn(data);

        // When
        DeviceTokenResponse response = service.pollToken("device123");

        // Then
        assertThat(response.error()).isEqualTo("authorization_pending");
        assertThat(response.accessToken()).isNull();
    }

    @Test
    void pollToken_returns_error_when_expired() {
        // Given
        when(valueOperations.get("device:code:expired123")).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> service.pollToken("expired123"))
            .isInstanceOf(DomainBadRequestException.class)
            .hasMessageContaining("error.deviceAuth.deviceCode.invalid");
    }

    @Test
    void authorizeDeviceCode_updates_status() {
        // Given
        DeviceCodeData data = new DeviceCodeData("device123", "ABCD-1234", DeviceCodeStatus.PENDING, null);
        when(valueOperations.get("device:usercode:ABCD-1234")).thenReturn("device123");
        when(valueOperations.get("device:code:device123")).thenReturn(data);

        // When
        service.authorizeDeviceCode("ABCD-1234", "42");

        // Then
        assertThat(data.getStatus()).isEqualTo(DeviceCodeStatus.AUTHORIZED);
        assertThat(data.getUserId()).isEqualTo("42");
        verify(valueOperations).set(eq("device:code:device123"), eq(data), eq(15L), eq(TimeUnit.MINUTES));
    }

    @Test
    void authorizeDeviceCode_accepts_linked_hash_map_from_redis_serializer() {
        Map<String, Object> redisValue = new HashMap<>();
        redisValue.put("deviceCode", "device123");
        redisValue.put("userCode", "ABCD-1234");
        redisValue.put("status", "PENDING");
        redisValue.put("userId", null);
        when(valueOperations.get("device:usercode:ABCD-1234")).thenReturn("device123");
        when(valueOperations.get("device:code:device123")).thenReturn(redisValue);

        service.authorizeDeviceCode("ABCD-1234", "42");

        ArgumentCaptor<DeviceCodeData> captor = ArgumentCaptor.forClass(DeviceCodeData.class);
        verify(valueOperations).set(eq("device:code:device123"), captor.capture(), eq(15L), eq(TimeUnit.MINUTES));
        assertThat(captor.getValue().getStatus()).isEqualTo(DeviceCodeStatus.AUTHORIZED);
        assertThat(captor.getValue().getUserId()).isEqualTo("42");
    }

    @Test
    void pollToken_accepts_linked_hash_map_from_redis_serializer() {
        Map<String, Object> redisValue = new HashMap<>();
        redisValue.put("deviceCode", "device123");
        redisValue.put("userCode", "ABCD-1234");
        redisValue.put("status", "AUTHORIZED");
        redisValue.put("userId", "42");
        when(valueOperations.get("device:code:device123")).thenReturn(redisValue);
        when(redisOperations.exec()).thenReturn(java.util.List.of("OK"));
        when(apiTokenService.createToken("42", "device-auth", "[]"))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_device_token", null));

        DeviceTokenResponse response = service.pollToken("device123");

        assertThat(response.error()).isNull();
        assertThat(response.accessToken()).isEqualTo("sk_device_token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(redisOperations).watch("device:code:device123");
        verify(redisOperations).multi();
        verify(valueOperations).set(eq("device:code:device123"), any(DeviceCodeData.class), eq(1L), eq(TimeUnit.MINUTES));
        verify(redisOperations).exec();
    }

    @Test
    void pollToken_returns_access_token_when_authorized() {
        DeviceCodeData data = new DeviceCodeData("device123", "ABCD-1234", DeviceCodeStatus.AUTHORIZED, "42");
        when(valueOperations.get("device:code:device123")).thenReturn(data);
        when(redisOperations.exec()).thenReturn(java.util.List.of("OK"));
        when(apiTokenService.createToken("42", "device-auth", "[]"))
                .thenReturn(new ApiTokenService.TokenCreateResult("sk_device_token", null));

        DeviceTokenResponse response = service.pollToken("device123");

        assertThat(response.error()).isNull();
        assertThat(response.accessToken()).isEqualTo("sk_device_token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(redisOperations).watch("device:code:device123");
        verify(redisOperations).multi();
        verify(valueOperations).set(eq("device:code:device123"), eq(data), eq(1L), eq(TimeUnit.MINUTES));
        verify(redisOperations).exec();
    }
}
