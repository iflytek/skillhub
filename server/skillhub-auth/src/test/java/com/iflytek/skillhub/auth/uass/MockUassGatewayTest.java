package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MockUassGatewayTest {

    @Test
    void buildLoginUrlPointsToMockLoginPageWhenMockModeIsEnabled() {
        MockUassLoginCoordinator coordinator = Mockito.mock(MockUassLoginCoordinator.class);
        MockUassGateway gateway = new MockUassGateway(mockProperties("mock://self"), coordinator);

        String loginUrl = gateway.buildLoginUrl(new UassLoginUrlRequest(
                "state-1",
                URI.create("http://localhost/api/v1/auth/uass/callback")
        ));

        assertThat(loginUrl).isEqualTo("http://localhost/mock-uass?state=state-1&callbackUrl=http://localhost/api/v1/auth/uass/callback");
    }

    @Test
    void validateLoginAndProfileUseMockIdentityShape() {
        MockUassLoginCoordinator coordinator = Mockito.mock(MockUassLoginCoordinator.class);
        when(coordinator.validateLogin("mock-login-1")).thenReturn(new UassValidatedLogin(
                "mock-user-2",
                "mock-access-token-mock-login-1",
                null,
                java.time.Instant.now().plusSeconds(300),
                java.util.Map.of(MockUassLoginCoordinator.ATTRIBUTE_LOGIN_CODE, "mock-login-1")
        ));
        when(coordinator.loadUserProfile(Mockito.any())).thenReturn(new UassRemoteUserProfile(
                "mock-user-2",
                "Mock User 2",
                "mock-user-2@example.com",
                "13800000000",
                "mock-user-2",
                java.util.Map.of("mode", "mock")
        ));
        MockUassGateway gateway = new MockUassGateway(mockProperties("mock://self"), coordinator);

        UassValidatedLogin login = gateway.validateLogin(new UassLoginValidationRequest(
                "mock-login-1",
                "state-1",
                URI.create("http://localhost/api/v1/auth/uass/callback")
        ));
        UassRemoteUserProfile profile = gateway.loadUserProfile(new UassSessionDescriptor(
                login.userCode(),
                login.accessToken(),
                login.refreshToken(),
                login.accessTokenExpiresAt(),
                login.attributes()
        ));

        assertThat(login.userCode()).isEqualTo("mock-user-2");
        assertThat(profile.userCode()).isEqualTo("mock-user-2");
        assertThat(profile.displayName()).isEqualTo("Mock User 2");
        assertThat(profile.email()).isEqualTo("mock-user-2@example.com");
    }

    @Test
    void validateLogin_defaultsUserCodeWhenLoginCodeIsBlank() {
        MockUassLoginCoordinator coordinator = Mockito.mock(MockUassLoginCoordinator.class);
        when(coordinator.validateLogin(MockUassGateway.DEFAULT_USER_CODE)).thenReturn(new UassValidatedLogin(
                MockUassGateway.DEFAULT_USER_CODE,
                "mock-access-token-default",
                null,
                java.time.Instant.now().plusSeconds(300),
                java.util.Map.of(MockUassLoginCoordinator.ATTRIBUTE_LOGIN_CODE, MockUassGateway.DEFAULT_USER_CODE)
        ));
        MockUassGateway gateway = new MockUassGateway(mockProperties("mock://self"), coordinator);

        UassValidatedLogin login = gateway.validateLogin(new UassLoginValidationRequest(
                " ",
                "state-1",
                URI.create("http://localhost/api/v1/auth/uass/callback")
        ));

        assertThat(login.userCode()).isEqualTo(MockUassGateway.DEFAULT_USER_CODE);
        assertThat(gateway.checkLoginStatus(new UassSessionDescriptor("user", null, null, null, null))).isTrue();
        gateway.logout(new UassSessionDescriptor("user", null, null, null, null));
    }

    @Test
    void operationsFailFastWhenNoGatewayImplementationIsConfigured() {
        MockUassGateway gateway = new MockUassGateway(
                mockProperties("https://uass.example.com"),
                Mockito.mock(MockUassLoginCoordinator.class)
        );

        assertThatThrownBy(() -> gateway.buildLoginUrl(new UassLoginUrlRequest(
                "state-1",
                URI.create("http://localhost/api/v1/auth/uass/callback")
        )))
                .isInstanceOf(UassClientException.class)
                .hasMessageContaining("No UASS gateway implementation configured");
        assertThatThrownBy(() -> gateway.checkLoginStatus(new UassSessionDescriptor("user", null, null, null, null)))
                .isInstanceOf(UassClientException.class)
                .hasMessageContaining("No UASS gateway implementation configured");
    }

    private static UassProperties mockProperties(String baseUrl) {
        UassProperties properties = new UassProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }
}
