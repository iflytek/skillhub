package com.iflytek.skillhub.auth.uass;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UassSpiPublicSurfaceTest {

    @Test
    void gatewayAndPayloadTypesArePublicForExternalIntegrations() {
        assertThat(Modifier.isPublic(UassGateway.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(UassLoginUrlRequest.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(UassLoginValidationRequest.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(UassValidatedLogin.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(UassSessionDescriptor.class.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(UassRemoteUserProfile.class.getModifiers())).isTrue();
    }
}
