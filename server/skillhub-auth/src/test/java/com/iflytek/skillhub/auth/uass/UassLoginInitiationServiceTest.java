package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UassLoginInitiationServiceTest {

    @Mock
    private UassClientFacade uassClientFacade;

    @Mock
    private UassLoginStateService uassLoginStateService;

    private UassLoginInitiationService service;

    @BeforeEach
    void setUp() {
        UassProperties properties = new UassProperties();
        properties.setCallbackPath("/api/v1/auth/uass/callback");
        service = new UassLoginInitiationService(uassClientFacade, uassLoginStateService, properties);
    }

    @Test
    void buildLoginUrl_startsStateAndUsesConfiguredCallbackPath() {
        when(uassLoginStateService.startLogin("/dashboard/publish", null)).thenReturn("state-1");
        when(uassClientFacade.buildLoginUrl("state-1", URI.create("https://skillhub.example.com/api/v1/auth/uass/callback")))
                .thenReturn("https://uass.example.com/login?state=state-1");

        String loginUrl = service.buildLoginUrl(
                "/dashboard/publish",
                URI.create("https://skillhub.example.com/api/v1/auth/uass/login-url")
        );

        assertThat(loginUrl).isEqualTo("https://uass.example.com/login?state=state-1");
    }

    @Test
    void buildLoginUrl_clearsStoredStateWhenProviderUrlBuildFails() {
        when(uassLoginStateService.startLogin("/dashboard", null)).thenReturn("state-2");
        when(uassClientFacade.buildLoginUrl("state-2", URI.create("http://localhost/api/v1/auth/uass/callback")))
                .thenThrow(new UassClientException("buildLoginUrl", "boom"));

        assertThatThrownBy(() -> service.buildLoginUrl("/dashboard", URI.create("http://localhost/api/v1/auth/uass/redirect")))
                .isInstanceOf(UassClientException.class)
                .hasMessageContaining("boom");

        verify(uassLoginStateService).clearFailedCallback("state-2");
    }
}
