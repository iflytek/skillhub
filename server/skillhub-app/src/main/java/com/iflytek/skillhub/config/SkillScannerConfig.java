package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.http.WebClientHttpClient;
import com.iflytek.skillhub.infra.scanner.ScanOptions;
import com.iflytek.skillhub.infra.scanner.SkillScannerAdapter;
import com.iflytek.skillhub.infra.scanner.SkillScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class SkillScannerConfig {

    private static final Logger log = LoggerFactory.getLogger(SkillScannerConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
    public HttpClient scannerHttpClient(SkillScannerProperties properties) {
        int readTimeoutMs = properties.getReadTimeoutMs();
        int connectTimeoutMs = properties.getConnectTimeoutMs();

        log.info("Creating scanner-specific HttpClient with connectTimeout={}ms, readTimeout={}ms",
                connectTimeoutMs, readTimeoutMs);

        reactor.netty.http.client.HttpClient reactorClient = reactor.netty.http.client.HttpClient.create()
                .followRedirect(false)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024))
                .build();

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(reactorClient))
                .exchangeStrategies(strategies)
                .build();

        log.info("Scanner HttpClient created successfully with responseTimeout={}ms", readTimeoutMs);
        return new WebClientHttpClient(webClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
    public SkillScannerService skillScannerService(HttpClient scannerHttpClient,
                                                   SkillScannerProperties properties) {
        log.info("Creating SkillScannerService with baseUrl={}", properties.getBaseUrl());
        return new SkillScannerService(
                scannerHttpClient,
                properties.getBaseUrl(),
                properties.getScanPath(),
                properties.getHealthPath()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
    public SecurityScanner securityScanner(SkillScannerService skillScannerService,
                                           SkillScannerProperties properties) {
        ScanOptions scanOptions = buildScanOptions(properties);
        return new SkillScannerAdapter(skillScannerService, properties.getMode(), scanOptions);
    }

    private ScanOptions buildScanOptions(SkillScannerProperties properties) {
        SkillScannerProperties.Analyzers analyzers = properties.getAnalyzers();
        return new ScanOptions(
                analyzers.isBehavioral(),
                analyzers.isLlm(),
                analyzers.getLlmProvider(),
                analyzers.isMeta(),
                analyzers.isAiDefense(),
                analyzers.getAiDefenseApiKey(),
                analyzers.isVirusTotal(),
                analyzers.isTrigger()
        );
    }

    @Bean
    @ConditionalOnMissingBean(ScanTaskProducer.class)
    @ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "false", matchIfMissing = true)
    public ScanTaskProducer noOpScanTaskProducer() {
        return task -> {
        };
    }
}
