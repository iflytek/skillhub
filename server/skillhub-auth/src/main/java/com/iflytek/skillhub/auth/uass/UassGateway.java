package com.iflytek.skillhub.auth.uass;

/**
 * Public SPI for wiring a real enterprise UASS implementation into SkillHub.
 *
 * <p>Deployments should provide a Spring bean implementing this interface. The
 * default {@link MockUassGateway} remains available for local verification.
 */
public interface UassGateway {

    String buildLoginUrl(UassLoginUrlRequest request);

    UassValidatedLogin validateLogin(UassLoginValidationRequest request);

    boolean checkLoginStatus(UassSessionDescriptor session);

    UassRemoteUserProfile loadUserProfile(UassSessionDescriptor session);

    void logout(UassSessionDescriptor session);
}
