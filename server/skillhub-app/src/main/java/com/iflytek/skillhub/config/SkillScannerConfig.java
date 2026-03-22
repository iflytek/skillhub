package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.scanner.ScanOptions;
import com.iflytek.skillhub.infra.scanner.SkillScannerAdapter;
import com.iflytek.skillhub.infra.scanner.SkillScannerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillScannerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
    public SkillScannerService skillScannerService(HttpClient httpClient,
                                                   SkillScannerProperties properties) {
        return new SkillScannerService(
                httpClient,
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
