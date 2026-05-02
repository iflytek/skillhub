package com.iflytek.skillhub.auth.uass;

import java.net.URI;
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
    static final String MOCK_LOGIN_PATH = "/mock-uass";

    private final UassProperties uassProperties;
    private final MockUassLoginCoordinator mockUassLoginCoordinator;

    MockUassGateway(UassProperties uassProperties, MockUassLoginCoordinator mockUassLoginCoordinator) {
        this.uassProperties = Objects.requireNonNull(uassProperties, "uassProperties must not be null");
        this.mockUassLoginCoordinator = Objects.requireNonNull(mockUassLoginCoordinator, "mockUassLoginCoordinator must not be null");
    }

    @Override
    public String buildLoginUrl(UassLoginUrlRequest request) {
        assertMockMode("buildLoginUrl");
        return UriComponentsBuilder.fromUri(resolveMockLoginBaseUri(request.callbackUri()))
                .replacePath(resolveMockLoginPath(resolveMockLoginBaseUri(request.callbackUri()).getPath()))
                .replaceQuery(null)
                .fragment(null)
                .queryParam("state", request.state())
                .queryParam("callbackUrl", request.callbackUri())
                .build(true)
                .toUriString();
    }

    @Override
    public UassValidatedLogin validateLogin(UassLoginValidationRequest request) {
        assertMockMode("validateLogin");
        String loginCode = StringUtils.hasText(request.loginCode()) ? request.loginCode().trim() : DEFAULT_USER_CODE;
        return mockUassLoginCoordinator.validateLogin(loginCode);
    }

    @Override
    public boolean checkLoginStatus(UassSessionDescriptor session) {
        assertMockMode("checkLoginStatus");
        return StringUtils.hasText(session.userCode());
    }

    @Override
    public UassRemoteUserProfile loadUserProfile(UassSessionDescriptor session) {
        assertMockMode("loadUserProfile");
        return mockUassLoginCoordinator.loadUserProfile(session);
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

    private String resolveMockLoginPath(String callbackPath) {
        String configuredCallbackPath = uassProperties.getCallbackPath();
        if (StringUtils.hasText(callbackPath)
                && StringUtils.hasText(configuredCallbackPath)
                && callbackPath.endsWith(configuredCallbackPath)) {
            String prefix = callbackPath.substring(0, callbackPath.length() - configuredCallbackPath.length());
            return prefix + MOCK_LOGIN_PATH;
        }
        return MOCK_LOGIN_PATH;
    }

    private URI resolveMockLoginBaseUri(URI callbackUri) {
        if (StringUtils.hasText(uassProperties.getMockLoginBaseUrl())) {
            return URI.create(uassProperties.getMockLoginBaseUrl());
        }
        return callbackUri;
    }
}
