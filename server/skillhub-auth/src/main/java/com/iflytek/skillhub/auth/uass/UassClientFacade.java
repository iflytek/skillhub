package com.iflytek.skillhub.auth.uass;

import java.net.URI;
import java.util.Objects;

/**
 * Platform-facing adapter for enterprise UASS operations. Application services
 * interact with this facade instead of depending on private jar contracts.
 */
public class UassClientFacade {

    private final UassGateway gateway;

    public UassClientFacade(UassGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    public String buildLoginUrl(String state, URI callbackUri) {
        String normalizedState = requireText(state, "state");
        URI normalizedCallbackUri = requireCallbackUri(callbackUri);
        try {
            return requireText(
                    gateway.buildLoginUrl(new UassLoginUrlRequest(normalizedState, normalizedCallbackUri)),
                    "loginUrl"
            );
        } catch (UassClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure("buildLoginUrl", "Failed to build UASS login URL", exception);
        }
    }

    public UassLoginContext validateLogin(String loginCode, String state, URI callbackUri) {
        String normalizedLoginCode = requireText(loginCode, "loginCode");
        String normalizedState = requireText(state, "state");
        URI normalizedCallbackUri = requireCallbackUri(callbackUri);
        try {
            UassValidatedLogin validatedLogin = gateway.validateLogin(
                    new UassLoginValidationRequest(normalizedLoginCode, normalizedState, normalizedCallbackUri)
            );
            return toLoginContext(validatedLogin, normalizedState, normalizedCallbackUri);
        } catch (UassClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure("validateLogin", "Failed to validate UASS login", exception);
        }
    }

    public boolean checkLoginStatus(UassLoginContext loginContext) {
        UassSessionDescriptor session = toSessionDescriptor(loginContext);
        try {
            return gateway.checkLoginStatus(session);
        } catch (RuntimeException exception) {
            throw failure("checkLoginStatus", "Failed to check UASS login status", exception);
        }
    }

    public UassUserProfile loadUserProfile(UassLoginContext loginContext) {
        UassSessionDescriptor session = toSessionDescriptor(loginContext);
        try {
            return toUserProfile(gateway.loadUserProfile(session));
        } catch (UassClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure("loadUserProfile", "Failed to load UASS user profile", exception);
        }
    }

    public void logout(UassLoginContext loginContext) {
        UassSessionDescriptor session = toSessionDescriptor(loginContext);
        try {
            gateway.logout(session);
        } catch (RuntimeException exception) {
            throw failure("logout", "Failed to logout from UASS", exception);
        }
    }

    private static UassLoginContext toLoginContext(UassValidatedLogin validatedLogin,
                                                   String state,
                                                   URI callbackUri) {
        if (validatedLogin == null) {
            throw failure("validateLogin", "UASS login validation returned no result");
        }
        return new UassLoginContext(
                state,
                callbackUri,
                requireText(validatedLogin.userCode(), "userCode"),
                validatedLogin.accessToken(),
                validatedLogin.refreshToken(),
                validatedLogin.accessTokenExpiresAt(),
                validatedLogin.attributes()
        );
    }

    private static UassUserProfile toUserProfile(UassRemoteUserProfile userProfile) {
        if (userProfile == null) {
            throw failure("loadUserProfile", "UASS user profile lookup returned no result");
        }
        return new UassUserProfile(
                requireText(userProfile.userCode(), "userCode"),
                userProfile.displayName(),
                userProfile.email(),
                userProfile.mobile(),
                userProfile.employeeNumber(),
                userProfile.attributes()
        );
    }

    private static UassSessionDescriptor toSessionDescriptor(UassLoginContext loginContext) {
        Objects.requireNonNull(loginContext, "loginContext must not be null");
        return new UassSessionDescriptor(
                requireText(loginContext.userCode(), "userCode"),
                loginContext.accessToken(),
                loginContext.refreshToken(),
                loginContext.accessTokenExpiresAt(),
                loginContext.attributes()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static URI requireCallbackUri(URI callbackUri) {
        return Objects.requireNonNull(callbackUri, "callbackUri must not be null");
    }

    private static UassClientException failure(String operation, String message) {
        return new UassClientException(operation, message);
    }

    private static UassClientException failure(String operation, String message, RuntimeException cause) {
        return new UassClientException(operation, message, cause);
    }
}
