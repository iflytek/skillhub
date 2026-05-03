package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.iflytek.skillhub.auth.exception.AuthFlowException;

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

    @Test
    void submitLogin_defaultsDisplayNameAndTrimsOptionalFields() {
        UassProperties properties = new UassProperties();
        properties.setBaseUrl(MockUassGateway.MOCK_BASE_URL);
        MockUassLoginCoordinator coordinator = new MockUassLoginCoordinator(properties);

        String redirectUrl = coordinator.submitLogin(
                " state-2 ",
                " http://localhost:3000/callback ",
                " U-2 ",
                " ",
                " 13800000001 ",
                " "
        );

        String loginCode = URI.create(redirectUrl).getQuery().replaceFirst("^loginCode=", "").replaceFirst("&state=.*$", "");
        UassValidatedLogin login = coordinator.validateLogin(loginCode);
        UassRemoteUserProfile profile = coordinator.loadUserProfile(new UassSessionDescriptor(
                login.userCode(),
                login.accessToken(),
                login.refreshToken(),
                login.accessTokenExpiresAt(),
                login.attributes()
        ));

        assertThat(redirectUrl).contains("state=state-2");
        assertThat(profile.displayName()).isEqualTo("U-2");
        assertThat(profile.mobile()).isEqualTo("13800000001");
        assertThat(profile.email()).isNull();
    }

    @Test
    void loadUserProfile_requiresMockLoginCodeInSession() {
        UassProperties properties = new UassProperties();
        properties.setBaseUrl(MockUassGateway.MOCK_BASE_URL);
        MockUassLoginCoordinator coordinator = new MockUassLoginCoordinator(properties);

        assertThatThrownBy(() -> coordinator.loadUserProfile(new UassSessionDescriptor(
                "user",
                "token",
                null,
                null,
                Map.of()
        )))
                .isInstanceOf(UassClientException.class)
                .hasMessageContaining("Missing mock login code in session attributes");
    }

    @Test
    void validateLogin_rejectsUnknownOrExpiredLoginCodes() {
        UassProperties unknownProperties = new UassProperties();
        unknownProperties.setBaseUrl(MockUassGateway.MOCK_BASE_URL);
        MockUassLoginCoordinator unknownCoordinator = new MockUassLoginCoordinator(unknownProperties);

        assertThatThrownBy(() -> unknownCoordinator.validateLogin("missing-code"))
                .isInstanceOf(UassClientException.class)
                .hasMessageContaining("Mock UASS login has expired or does not exist");

        UassProperties expiredProperties = new UassProperties();
        expiredProperties.setBaseUrl(MockUassGateway.MOCK_BASE_URL);
        expiredProperties.setStateTtl(Duration.ofSeconds(-1));
        MockUassLoginCoordinator expiredCoordinator = new MockUassLoginCoordinator(expiredProperties);
        String redirectUrl = expiredCoordinator.submitLogin(
                "state-3",
                "http://localhost/callback",
                "uass-expired",
                "Expired User",
                null,
                null
        );
        String loginCode = URI.create(redirectUrl).getQuery().replaceFirst("^loginCode=", "").replaceFirst("&state=.*$", "");

        assertThatThrownBy(() -> expiredCoordinator.validateLogin(loginCode))
                .isInstanceOf(UassClientException.class)
                .hasMessageContaining("Mock UASS login has expired or does not exist");
    }

    @Test
    void operations_failWhenMockModeIsDisabledOrRequiredFieldsBlank() {
        UassProperties disabledProperties = new UassProperties();
        disabledProperties.setBaseUrl("https://uass.example.com");
        MockUassLoginCoordinator disabledCoordinator = new MockUassLoginCoordinator(disabledProperties);

        assertThatThrownBy(() -> disabledCoordinator.submitLogin(
                "state-1",
                "http://localhost/callback",
                "uass-1",
                "User",
                null,
                null
        ))
                .isInstanceOf(AuthFlowException.class)
                .hasMessageContaining("error.auth.uass.mock.disabled");

        UassProperties enabledProperties = new UassProperties();
        enabledProperties.setBaseUrl(MockUassGateway.MOCK_BASE_URL);
        MockUassLoginCoordinator enabledCoordinator = new MockUassLoginCoordinator(enabledProperties);

        assertThatThrownBy(() -> enabledCoordinator.submitLogin(
                " ",
                "http://localhost/callback",
                "uass-1",
                "User",
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state must not be blank");
    }
}
