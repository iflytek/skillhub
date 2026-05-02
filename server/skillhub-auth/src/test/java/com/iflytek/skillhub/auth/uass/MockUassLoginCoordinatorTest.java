package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class MockUassLoginCoordinatorTest {

    @Test
    void submitLoginStoresProfileForLaterValidationAndProfileLookup() {
        UassProperties properties = new UassProperties();
        properties.setBaseUrl(MockUassGateway.MOCK_BASE_URL);
        MockUassLoginCoordinator coordinator = new MockUassLoginCoordinator(properties);

        String redirectUrl = coordinator.submitLogin(
                "state-1",
                "http://localhost:3000/api/v1/auth/uass/callback",
                "uass-001",
                "张三",
                "13800000000",
                "zhangsan@example.com"
        );

        assertThat(redirectUrl).startsWith("http://localhost:3000/api/v1/auth/uass/callback?loginCode=");
        assertThat(redirectUrl).contains("&state=state-1");

        String loginCode = URI.create(redirectUrl).getQuery().replaceFirst("^loginCode=", "").replaceFirst("&state=.*$", "");
        UassValidatedLogin login = coordinator.validateLogin(loginCode);
        UassRemoteUserProfile profile = coordinator.loadUserProfile(new UassSessionDescriptor(
                login.userCode(),
                login.accessToken(),
                login.refreshToken(),
                login.accessTokenExpiresAt(),
                login.attributes()
        ));

        assertThat(login.userCode()).isEqualTo("uass-001");
        assertThat(profile.userCode()).isEqualTo("uass-001");
        assertThat(profile.displayName()).isEqualTo("张三");
        assertThat(profile.mobile()).isEqualTo("13800000000");
        assertThat(profile.email()).isEqualTo("zhangsan@example.com");
    }
}
