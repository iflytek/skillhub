package com.iflytek.skillhub.infra.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();

        HttpClient reactorClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(5));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(reactorClient))
                .exchangeStrategies(strategies);
    }

    @Bean
    public com.iflytek.skillhub.infra.http.HttpClient httpClient(WebClient.Builder webClientBuilder) {
        return new WebClientHttpClient(webClientBuilder.build());
    }
}
