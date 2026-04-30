package com.iflytek.skillhub.auth.device;

import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        service = new DeviceAuthService(redisTemplate, apiTokenService, "/cli/auth");
    }

    @Test
    void generateDeviceCode_persistsDeviceAndUserCodeMappings() {
        DeviceCodeResponse response = service.generateDeviceCode();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(valueOperations, times(2)).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture(), unitCaptor.capture());

        assertThat(response.verificationUri()).isEqualTo("/cli/auth");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.interval()).isEqualTo(5);
        assertThat(response.userCode()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(response.deviceCode()).isNotBlank();

        assertThat(keyCaptor.getAllValues()).containsExactly(
                "device:code:" + response.deviceCode(),
                "device:usercode:" + response.userCode()
        );
        assertThat(ttlCaptor.getAllValues()).containsExactly(15L, 15L);
        assertThat(unitCaptor.getAllValues()).containsExactly(TimeUnit.MINUTES, TimeUnit.MINUTES);
        assertThat(valueCaptor.getAllValues().get(0))
                .isInstanceOf(DeviceCodeData.class)
                .extracting("deviceCode", "userCode", "status", "userId")
                .containsExactly(response.deviceCode(), response.userCode(), DeviceCodeStatus.PENDING, null);
        assertThat(valueCaptor.getAllValues().get(1)).isEqualTo(response.deviceCode());
    }

    @Test
    void authorizeDeviceCode_marksPendingCodeAsAuthorized() {
        DeviceCodeData pending = new DeviceCodeData("device-1", "ABCD-EFGH", DeviceCodeStatus.PENDING, null);
        when(valueOperations.get("device:usercode:ABCD-EFGH")).thenReturn("device-1");
        when(valueOperations.get("device:code:device-1")).thenReturn(pending);

        service.authorizeDeviceCode("ABCD-EFGH", "user-1");

        ArgumentCaptor<Object> dataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(eq("device:code:device-1"), dataCaptor.capture(), eq(15L), eq(TimeUnit.MINUTES));
        DeviceCodeData stored = (DeviceCodeData) dataCaptor.getValue();
        assertThat(stored.getStatus()).isEqualTo(DeviceCodeStatus.AUTHORIZED);
        assertThat(stored.getUserId()).isEqualTo("user-1");
    }

    @Test
    void authorizeDeviceCode_rejectsDifferentUserWhenAlreadyAuthorized() {
        DeviceCodeData authorized = new DeviceCodeData("device-1", "ABCD-EFGH", DeviceCodeStatus.AUTHORIZED, "user-1");
        when(valueOperations.get("device:usercode:ABCD-EFGH")).thenReturn("device-1");
        when(valueOperations.get("device:code:device-1")).thenReturn(authorized);

        assertThatThrownBy(() -> service.authorizeDeviceCode("ABCD-EFGH", "user-2"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.deviceAuth.deviceCode.alreadyAuthorized");
    }

    @Test
    void pollToken_returnsPendingWhileAwaitingApproval() {
        when(valueOperations.get("device:code:device-1"))
                .thenReturn(new DeviceCodeData("device-1", "ABCD-EFGH", DeviceCodeStatus.PENDING, null));

        DeviceTokenResponse response = service.pollToken("device-1");

        assertThat(response.accessToken()).isNull();
        assertThat(response.tokenType()).isNull();
        assertThat(response.error()).isEqualTo("authorization_pending");
    }

    @Test
    void pollToken_redeemsAuthorizedCodeExactlyOnce() {
        DeviceCodeData authorized = new DeviceCodeData("device-1", "ABCD-EFGH", DeviceCodeStatus.AUTHORIZED, "user-1");
        when(valueOperations.get("device:code:device-1")).thenReturn(authorized);
        when(valueOperations.setIfAbsent("device:claim:device-1", "claimed", 1L, TimeUnit.MINUTES)).thenReturn(true);
        when(apiTokenService.rotateToken("user-1", "CLI Device Flow", "[\"skill:read\",\"skill:publish\"]"))
                .thenReturn(new ApiTokenService.TokenCreateResult("raw-token", null));

        DeviceTokenResponse response = service.pollToken("device-1");

        assertThat(response.accessToken()).isEqualTo("raw-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.error()).isNull();
        verify(valueOperations).set(eq("device:code:device-1"), any(DeviceCodeData.class), eq(1L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete("device:usercode:ABCD-EFGH");
    }

    @Test
    void pollToken_releasesClaimWhenAuthorizedCodeHasNoUser() {
        DeviceCodeData authorized = new DeviceCodeData("device-1", "ABCD-EFGH", DeviceCodeStatus.AUTHORIZED, " ");
        when(valueOperations.get("device:code:device-1")).thenReturn(authorized);
        when(valueOperations.setIfAbsent("device:claim:device-1", "claimed", 1L, TimeUnit.MINUTES)).thenReturn(true);

        assertThatThrownBy(() -> service.pollToken("device-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.deviceAuth.deviceCode.invalid");

        verify(redisTemplate).delete("device:claim:device-1");
    }

    @Test
    void pollToken_rejectsAlreadyClaimedAuthorizedCode() {
        DeviceCodeData authorized = new DeviceCodeData("device-1", "ABCD-EFGH", DeviceCodeStatus.AUTHORIZED, "user-1");
        when(valueOperations.get("device:code:device-1")).thenReturn(authorized);
        when(valueOperations.setIfAbsent("device:claim:device-1", "claimed", 1L, TimeUnit.MINUTES)).thenReturn(false);

        assertThatThrownBy(() -> service.pollToken("device-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.deviceAuth.deviceCode.used");
    }
}
