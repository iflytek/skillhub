package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UassGatewayConfigurationTest {

    private final UassGatewayConfiguration configuration = new UassGatewayConfiguration();

    @Test
    void uassGateway_defaultsToMockGateway() {
        UassProperties properties = new UassProperties();
        MockUassLoginCoordinator coordinator = org.mockito.Mockito.mock(MockUassLoginCoordinator.class);

        UassGateway gateway = configuration.uassGateway(properties, coordinator);

        assertThat(gateway).isInstanceOf(MockUassGateway.class);
    }

    @Test
    void uassClientFacade_wrapsProvidedGateway() {
        UassGateway gateway = new UassGateway() {
            @Override
            public String buildLoginUrl(UassLoginUrlRequest request) {
                return "https://uass.example.com/login";
            }

            @Override
            public UassValidatedLogin validateLogin(UassLoginValidationRequest request) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public boolean checkLoginStatus(UassSessionDescriptor session) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public UassRemoteUserProfile loadUserProfile(UassSessionDescriptor session) {
                throw new UnsupportedOperationException("not used");
            }

            @Override
            public void logout(UassSessionDescriptor session) {
                throw new UnsupportedOperationException("not used");
            }
        };
        UassClientFacade facade = configuration.uassClientFacade(gateway);

        assertThat(facade).isNotNull();
        assertThat(facade.buildLoginUrl(
                "state-1",
                java.net.URI.create("https://skillhub.example.com/api/v1/auth/uass/callback")
        )).isEqualTo("https://uass.example.com/login");
    }
}
