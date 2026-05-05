package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.infra.http.HttpClient;
import com.iflytek.skillhub.infra.scanner.SkillScannerAdapter;
import com.iflytek.skillhub.infra.scanner.SkillScannerService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SkillScannerConfigTest {

    @Test
    void constructor_createsInstance() {
        SkillScannerConfig config = new SkillScannerConfig();
        assertThat(config).isNotNull();
    }

    @Test
    void scannerHttpClient_createsHttpClient() {
        SkillScannerConfig config = new SkillScannerConfig();
        SkillScannerProperties properties = new SkillScannerProperties();
        properties.setEnabled(true);
        properties.setConnectTimeoutMs(5000);
        properties.setReadTimeoutMs(300000);

        HttpClient httpClient = config.scannerHttpClient(properties);

        assertThat(httpClient).isNotNull();
    }

    @Test
    void skillScannerService_createsService() {
        SkillScannerConfig config = new SkillScannerConfig();
        SkillScannerProperties properties = new SkillScannerProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:8000");
        properties.setScanPath("/scan-upload");
        properties.setHealthPath("/health");

        HttpClient httpClient = mock(HttpClient.class);

        SkillScannerService service = config.skillScannerService(httpClient, properties);

        assertThat(service).isNotNull();
    }

    @Test
    void securityScanner_createsScanner() {
        SkillScannerConfig config = new SkillScannerConfig();
        SkillScannerProperties properties = new SkillScannerProperties();
        properties.setEnabled(true);
        properties.setMode("local");
        properties.getAnalyzers().setBehavioral(true);
        properties.getAnalyzers().setLlm(true);
        properties.getAnalyzers().setLlmProvider("anthropic");
        properties.getAnalyzers().setMeta(true);
        properties.getAnalyzers().setAiDefense(true);
        properties.getAnalyzers().setAiDefenseApiKey("key");
        properties.getAnalyzers().setVirusTotal(true);
        properties.getAnalyzers().setTrigger(true);

        SkillScannerService service = new SkillScannerService(
                mock(HttpClient.class),
                "http://localhost:8000",
                "/scan-upload",
                "/health"
        );

        SecurityScanner scanner = config.securityScanner(service, properties);

        assertThat(scanner).isNotNull();
        assertThat(scanner).isInstanceOf(SkillScannerAdapter.class);
    }

    @Test
    void noOpScanTaskProducer_createsNoOpProducer() {
        SkillScannerConfig config = new SkillScannerConfig();
        ScanTaskProducer producer = config.noOpScanTaskProducer();

        assertThat(producer).isNotNull();
        producer.publishScanTask(new ScanTask("task-1", 1L, "path", "key", "pub", 0L, java.util.Map.of()));
    }
}
