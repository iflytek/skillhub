package com.iflytek.skillhub.auth.uass;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UassClientFacadeTest {

    private static final URI CALLBACK_URI = URI.create("https://skillhub.example.com/api/v1/auth/uass/callback");

    @Test
    void buildLoginUrl_delegatesToGateway() {
        UassGateway gateway = mock(UassGateway.class);
        when(gateway.buildLoginUrl(any())).thenReturn("https://uass.example.com/login?state=state-1");
        UassClientFacade facade = new UassClientFacade(gateway);

        String loginUrl = facade.buildLoginUrl("state-1", CALLBACK_URI);

        ArgumentCaptor<UassLoginUrlRequest> requestCaptor = ArgumentCaptor.forClass(UassLoginUrlRequest.class);
        verify(gateway).buildLoginUrl(requestCaptor.capture());
        assertThat(requestCaptor.getValue().state()).isEqualTo("state-1");
        assertThat(requestCaptor.getValue().callbackUri()).isEqualTo(CALLBACK_URI);
        assertThat(loginUrl).isEqualTo("https://uass.example.com/login?state=state-1");
    }

    @Test
    void buildLoginUrl_wrapsGatewayFailureWithoutLeakingSensitiveState() {
        UassGateway gateway = mock(UassGateway.class);
        when(gateway.buildLoginUrl(any())).thenThrow(new IllegalStateException("sdk unavailable"));
        UassClientFacade facade = new UassClientFacade(gateway);

        assertThatThrownBy(() -> facade.buildLoginUrl("secret-state", CALLBACK_URI))
                .isInstanceOf(UassClientException.class)
                .hasMessage("Failed to build UASS login URL")
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(exception -> {
                    UassClientException uassException = (UassClientException) exception;
                    assertThat(uassException.getOperation()).isEqualTo("buildLoginUrl");
                    assertThat(uassException.getMessage()).doesNotContain("secret-state");
                });
    }

    @Test
    void validateLogin_mapsGatewayResponseToInternalContext() {
        UassGateway gateway = mock(UassGateway.class);
        Instant expiresAt = Instant.parse("2026-04-29T10:15:30Z");
        when(gateway.validateLogin(any())).thenReturn(
                new UassValidatedLogin(
                        "uass-user-1",
                        "access-token",
                        "refresh-token",
                        expiresAt,
                        Map.of("tenant", "acme")
                )
        );
        UassClientFacade facade = new UassClientFacade(gateway);

        UassLoginContext context = facade.validateLogin("auth-code", "state-1", CALLBACK_URI);

        ArgumentCaptor<UassLoginValidationRequest> requestCaptor =
                ArgumentCaptor.forClass(UassLoginValidationRequest.class);
        verify(gateway).validateLogin(requestCaptor.capture());
        assertThat(requestCaptor.getValue().loginCode()).isEqualTo("auth-code");
        assertThat(requestCaptor.getValue().state()).isEqualTo("state-1");
        assertThat(context.state()).isEqualTo("state-1");
        assertThat(context.callbackUri()).isEqualTo(CALLBACK_URI);
        assertThat(context.userCode()).isEqualTo("uass-user-1");
        assertThat(context.accessToken()).isEqualTo("access-token");
        assertThat(context.refreshToken()).isEqualTo("refresh-token");
        assertThat(context.accessTokenExpiresAt()).isEqualTo(expiresAt);
        assertThat(context.attributes()).containsEntry("tenant", "acme");
    }

    @Test
    void validateLogin_rejectsMissingUserCode() {
        UassGateway gateway = mock(UassGateway.class);
        when(gateway.validateLogin(any())).thenReturn(
                new UassValidatedLogin("", "access-token", null, null, Map.of())
        );
        UassClientFacade facade = new UassClientFacade(gateway);

        assertThatThrownBy(() -> facade.validateLogin("auth-code", "state-1", CALLBACK_URI))
                .isInstanceOf(UassClientException.class)
                .hasMessage("Failed to validate UASS login")
                .satisfies(exception ->
                        assertThat(((UassClientException) exception).getOperation()).isEqualTo("validateLogin"));
    }

    @Test
    void checkLoginStatus_delegatesUsingNormalizedSessionContext() {
        UassGateway gateway = mock(UassGateway.class);
        when(gateway.checkLoginStatus(any())).thenReturn(true);
        UassClientFacade facade = new UassClientFacade(gateway);

        boolean loggedIn = facade.checkLoginStatus(loginContext());

        ArgumentCaptor<UassSessionDescriptor> sessionCaptor =
                ArgumentCaptor.forClass(UassSessionDescriptor.class);
        verify(gateway).checkLoginStatus(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().userCode()).isEqualTo("uass-user-1");
        assertThat(sessionCaptor.getValue().accessToken()).isEqualTo("access-token");
        assertThat(loggedIn).isTrue();
    }

    @Test
    void loadUserProfile_mapsGatewayResponseToInternalProfile() {
        UassGateway gateway = mock(UassGateway.class);
        when(gateway.loadUserProfile(any())).thenReturn(
                new UassRemoteUserProfile(
                        "uass-user-1",
                        "Alice",
                        "alice@example.com",
                        "13800000000",
                        "E1001",
                        Map.of("department", "platform")
                )
        );
        UassClientFacade facade = new UassClientFacade(gateway);

        UassUserProfile profile = facade.loadUserProfile(loginContext());

        assertThat(profile.userCode()).isEqualTo("uass-user-1");
        assertThat(profile.displayName()).isEqualTo("Alice");
        assertThat(profile.email()).isEqualTo("alice@example.com");
        assertThat(profile.mobile()).isEqualTo("13800000000");
        assertThat(profile.employeeNumber()).isEqualTo("E1001");
        assertThat(profile.attributes()).containsEntry("department", "platform");
    }

    @Test
    void loadUserProfile_rejectsMissingUserCode() {
        UassGateway gateway = mock(UassGateway.class);
        when(gateway.loadUserProfile(any())).thenReturn(
                new UassRemoteUserProfile(" ", "Alice", null, null, null, Map.of())
        );
        UassClientFacade facade = new UassClientFacade(gateway);

        assertThatThrownBy(() -> facade.loadUserProfile(loginContext()))
                .isInstanceOf(UassClientException.class)
                .hasMessage("Failed to load UASS user profile")
                .satisfies(exception ->
                        assertThat(((UassClientException) exception).getOperation()).isEqualTo("loadUserProfile"));
    }

    @Test
    void logout_wrapsGatewayFailure() {
        UassGateway gateway = mock(UassGateway.class);
        doThrow(new IllegalStateException("upstream unavailable")).when(gateway).logout(any());
        UassClientFacade facade = new UassClientFacade(gateway);

        assertThatThrownBy(() -> facade.logout(loginContext()))
                .isInstanceOf(UassClientException.class)
                .hasMessage("Failed to logout from UASS")
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(exception ->
                        assertThat(((UassClientException) exception).getOperation()).isEqualTo("logout"));
    }

    private static UassLoginContext loginContext() {
        return new UassLoginContext(
                "state-1",
                CALLBACK_URI,
                "uass-user-1",
                "access-token",
                "refresh-token",
                Instant.parse("2026-04-29T10:15:30Z"),
                Map.of("tenant", "acme")
        );
    }
}
