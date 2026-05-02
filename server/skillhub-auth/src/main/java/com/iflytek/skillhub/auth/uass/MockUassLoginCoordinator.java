package com.iflytek.skillhub.auth.uass;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import com.iflytek.skillhub.auth.exception.AuthFlowException;

/**
 * Coordinates the local mock UASS browser flow used in development.
 *
 * <p>It accepts user-entered mock profile data, issues a transient loginCode,
 * and later lets {@link MockUassGateway} resolve that code into a validated
 * login result plus user profile.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.auth.uass", name = "enabled", havingValue = "true")
public class MockUassLoginCoordinator {

    static final String ATTRIBUTE_LOGIN_CODE = "mockLoginCode";

    private final UassProperties uassProperties;
    private final ConcurrentHashMap<String, StoredMockLogin> logins = new ConcurrentHashMap<>();

    public MockUassLoginCoordinator(UassProperties uassProperties) {
        this.uassProperties = uassProperties;
    }

    public String submitLogin(String state,
                              String callbackUrl,
                              String ussId,
                              String displayName,
                              String mobile,
                              String email) {
        assertMockMode("submitLogin");
        String normalizedState = requireText(state, "state");
        String normalizedCallbackUrl = requireText(callbackUrl, "callbackUrl");
        String normalizedUssId = requireText(ussId, "ussId");
        String normalizedDisplayName = normalizeOptional(displayName);
        String normalizedMobile = normalizeOptional(mobile);
        String normalizedEmail = normalizeOptional(email);

        String loginCode = UUID.randomUUID().toString();
        logins.put(loginCode, new StoredMockLogin(
                normalizedUssId,
                normalizedDisplayName == null ? normalizedUssId : normalizedDisplayName,
                normalizedMobile,
                normalizedEmail,
                Instant.now().plus(uassProperties.getStateTtl())
        ));

        return UriComponentsBuilder.fromUriString(normalizedCallbackUrl)
                .replaceQuery(null)
                .fragment(null)
                .queryParam("loginCode", loginCode)
                .queryParam("state", normalizedState)
                .build(true)
                .toUriString();
    }

    public UassValidatedLogin validateLogin(String loginCode) {
        assertMockMode("validateLogin");
        StoredMockLogin login = findRequired(loginCode, "validateLogin");
        return new UassValidatedLogin(
                login.ussId(),
                "mock-access-token-" + loginCode,
                null,
                Instant.now().plusSeconds(300),
                Map.of(
                        "mode", "mock",
                        ATTRIBUTE_LOGIN_CODE, loginCode
                )
        );
    }

    public UassRemoteUserProfile loadUserProfile(UassSessionDescriptor session) {
        assertMockMode("loadUserProfile");
        String loginCode = session.attributes().get(ATTRIBUTE_LOGIN_CODE);
        if (!StringUtils.hasText(loginCode)) {
            throw new UassClientException("loadUserProfile", "Missing mock login code in session attributes");
        }
        StoredMockLogin login = findRequired(loginCode, "loadUserProfile");
        return new UassRemoteUserProfile(
                login.ussId(),
                login.displayName(),
                login.email(),
                login.mobile(),
                login.ussId(),
                Map.of("mode", "mock")
        );
    }

    private StoredMockLogin findRequired(String loginCode, String operation) {
        String normalizedLoginCode = requireText(loginCode, "loginCode");
        StoredMockLogin login = logins.get(normalizedLoginCode);
        if (login == null) {
            throw new UassClientException(operation, "Mock UASS login has expired or does not exist");
        }
        if (login.expiresAt().isBefore(Instant.now())) {
            logins.remove(normalizedLoginCode);
            throw new UassClientException(operation, "Mock UASS login has expired or does not exist");
        }
        return login;
    }

    private void assertMockMode(String operation) {
        if (MockUassGateway.MOCK_BASE_URL.equalsIgnoreCase(uassProperties.getBaseUrl())) {
            return;
        }
        throw new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.uass.mock.disabled");
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record StoredMockLogin(
            String ussId,
            String displayName,
            String mobile,
            String email,
            Instant expiresAt
    ) {
    }
}
