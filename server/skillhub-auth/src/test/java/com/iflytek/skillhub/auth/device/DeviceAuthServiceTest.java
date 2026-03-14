package com.iflytek.skillhub.auth.device;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
    private ApiTokenService apiTokenService;

    private DeviceAuthService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DeviceAuthService(redisTemplate, apiTokenService, "https://skillhub.example.com/device");
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
    void pollToken_returns_access_token_when_authorized() {
        // Given
        DeviceCodeData data = new DeviceCodeData("device123", "ABCD-1234", DeviceCodeStatus.AUTHORIZED, "42");
        when(valueOperations.get("device:code:device123")).thenReturn(data);
        when(valueOperations.setIfAbsent("device:claim:device123", "claimed", 1L, TimeUnit.MINUTES)).thenReturn(true);
        when(apiTokenService.createToken("42", "CLI Device Flow", "[\"skill:read\",\"skill:publish\"]"))
            .thenReturn(new ApiTokenService.TokenCreateResult("sk_cli_token", mock(ApiToken.class)));

        // When
        DeviceTokenResponse response = service.pollToken("device123");

        // Then
        assertThat(response.accessToken()).isEqualTo("sk_cli_token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.error()).isNull();
        assertThat(data.getStatus()).isEqualTo(DeviceCodeStatus.USED);
        verify(valueOperations).set("device:code:device123", data, 1L, TimeUnit.MINUTES);
        verify(redisTemplate).delete("device:usercode:ABCD-1234");
    }

    @Test
    void pollToken_rejects_second_exchange_attempt() {
        // Given
        DeviceCodeData data = new DeviceCodeData("device123", "ABCD-1234", DeviceCodeStatus.AUTHORIZED, "42");
        when(valueOperations.get("device:code:device123")).thenReturn(data);
        when(valueOperations.setIfAbsent("device:claim:device123", "claimed", 1L, TimeUnit.MINUTES)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.pollToken("device123"))
            .isInstanceOf(DomainBadRequestException.class)
            .hasMessageContaining("error.deviceAuth.deviceCode.used");
        verify(apiTokenService, never()).createToken(anyString(), anyString(), anyString());
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
    void authorizeDeviceCode_rejects_different_user_after_authorization() {
        // Given
        DeviceCodeData data = new DeviceCodeData("device123", "ABCD-1234", DeviceCodeStatus.AUTHORIZED, "42");
        when(valueOperations.get("device:usercode:ABCD-1234")).thenReturn("device123");
        when(valueOperations.get("device:code:device123")).thenReturn(data);

        // When / Then
        assertThatThrownBy(() -> service.authorizeDeviceCode("ABCD-1234", "99"))
            .isInstanceOf(DomainBadRequestException.class)
            .hasMessageContaining("error.deviceAuth.deviceCode.alreadyAuthorized");
    }
}
