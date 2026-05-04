package com.iflytek.skillhub.infra.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientConfigTest {

    @Test
    void globalWebClientCustomizer_shouldReturnNonNullCustomizer() {
        WebClientConfig config = new WebClientConfig();
        WebClientCustomizer customizer = config.globalWebClientCustomizer();

        assertThat(customizer).isNotNull();
    }

    @Test
    void globalWebClientCustomizer_shouldApplyToBuilder() {
        WebClientConfig config = new WebClientConfig();
        WebClientCustomizer customizer = config.globalWebClientCustomizer();

        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.clientConnector(any())).thenReturn(builder);
        when(builder.exchangeStrategies(any(ExchangeStrategies.class))).thenReturn(builder);

        customizer.customize(builder);

        verify(builder).clientConnector(any());
        verify(builder).exchangeStrategies(any(ExchangeStrategies.class));
    }
}
