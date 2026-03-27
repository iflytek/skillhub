package com.iflytek.skillhub.infra.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Bean
    public WebClientCustomizer globalWebClientCustomizer() {
        log.info("Registering global WebClientCustomizer with 5-minute responseTimeout and 10-second connectTimeout");

        return builder -> {
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();

            reactor.netty.http.client.HttpClient reactorClient = reactor.netty.http.client.HttpClient.create()
                    .followRedirect(false)
                    .responseTimeout(Duration.ofMinutes(5))
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

            builder.clientConnector(new ReactorClientHttpConnector(reactorClient))
                   .exchangeStrategies(strategies);
        };
    }
}
