package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class MockUassGatewayTest {

    @Test
    void buildLoginUrlPointsBackToCallbackWhenMockModeIsEnabled() {
        MockUassGateway gateway = new MockUassGateway(mockProperties("mock://self"));

        String loginUrl = gateway.buildLoginUrl(new UassLoginUrlRequest(
                "state-1",
                URI.create("http://localhost/api/v1/auth/uass/callback")
        ));

        assertThat(loginUrl).isEqualTo("http://localhost/api/v1/auth/uass/callback?loginCode=uass-mock-user&state=state-1");
    }

    @Test
    void validateLoginAndProfileUseMockIdentityShape() {
        MockUassGateway gateway = new MockUassGateway(mockProperties("mock://self"));

        UassValidatedLogin login = gateway.validateLogin(new UassLoginValidationRequest(
                "mock-user-2",
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
        assertThat(profile.displayName()).isEqualTo("UASS Mock User");
        assertThat(profile.email()).isEqualTo("mock-user-2@skillhub.local");
    }

    @Test
    void operationsFailFastWhenNoGatewayImplementationIsConfigured() {
        MockUassGateway gateway = new MockUassGateway(mockProperties("https://uass.example.com"));

        assertThatThrownBy(() -> gateway.buildLoginUrl(new UassLoginUrlRequest(
                "state-1",
                URI.create("http://localhost/api/v1/auth/uass/callback")
        )))
                .isInstanceOf(UassClientException.class)
                .hasMessageContaining("No UASS gateway implementation configured");
    }

    private static UassProperties mockProperties(String baseUrl) {
        UassProperties properties = new UassProperties();
        properties.setBaseUrl(baseUrl);
        return properties;
    }
}
