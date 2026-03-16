package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.scanner.SkillScannerAdapter;
import com.iflytek.skillhub.infra.scanner.SkillScannerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillScannerConfig {

    @Bean
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
    public SecurityScanner securityScanner(SkillScannerService skillScannerService,
                                           SkillScannerProperties properties) {
        return new SkillScannerAdapter(skillScannerService, properties.getMode());
    }
}
