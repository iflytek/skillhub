package com.iflytek.skillhub.auth.uass;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Local fallback gateway that enables browser-flow verification without the
 * internal UASS jar. Real deployments should override {@link UassGateway} with
 * the enterprise adapter and keep the same facade surface.
 */
class MockUassGateway implements UassGateway {

    static final String MOCK_BASE_URL = "mock://self";
    static final String DEFAULT_USER_CODE = "uass-mock-user";

    private final UassProperties uassProperties;

    MockUassGateway(UassProperties uassProperties) {
        this.uassProperties = Objects.requireNonNull(uassProperties, "uassProperties must not be null");
    }

    @Override
    public String buildLoginUrl(UassLoginUrlRequest request) {
        assertMockMode("buildLoginUrl");
        return UriComponentsBuilder.fromUri(request.callbackUri())
                .queryParam("loginCode", DEFAULT_USER_CODE)
                .queryParam("state", request.state())
                .build(true)
                .toUriString();
    }

    @Override
    public UassValidatedLogin validateLogin(UassLoginValidationRequest request) {
        assertMockMode("validateLogin");
        String userCode = StringUtils.hasText(request.loginCode()) ? request.loginCode().trim() : DEFAULT_USER_CODE;
        return new UassValidatedLogin(
                userCode,
                "mock-access-token-" + userCode,
                null,
                Instant.now().plusSeconds(300),
                Map.of("mode", "mock")
        );
    }

    @Override
    public boolean checkLoginStatus(UassSessionDescriptor session) {
        assertMockMode("checkLoginStatus");
        return StringUtils.hasText(session.userCode());
    }

    @Override
    public UassRemoteUserProfile loadUserProfile(UassSessionDescriptor session) {
        assertMockMode("loadUserProfile");
        String userCode = StringUtils.hasText(session.userCode()) ? session.userCode().trim() : DEFAULT_USER_CODE;
        return new UassRemoteUserProfile(
                userCode,
                "UASS Mock User",
                userCode + "@skillhub.local",
                null,
                userCode,
                Map.of("mode", "mock")
        );
    }

    @Override
    public void logout(UassSessionDescriptor session) {
        assertMockMode("logout");
    }

    private void assertMockMode(String operation) {
        if (MOCK_BASE_URL.equalsIgnoreCase(uassProperties.getBaseUrl())) {
            return;
        }
        throw new UassClientException(
                operation,
                "No UASS gateway implementation configured; provide a UassGateway bean or set skillhub.auth.uass.base-url=mock://self for local verification"
        );
    }
}
