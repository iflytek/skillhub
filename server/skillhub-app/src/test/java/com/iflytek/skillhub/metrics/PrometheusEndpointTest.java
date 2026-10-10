package com.iflytek.skillhub.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class PrometheusEndpointTest {

    @Autowired
    private SkillHubMetrics skillHubMetrics;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Environment environment;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void metricsRegistry_stillRecordsCustomMetrics_whenPrometheusEndpointIsDisabled() {
        skillHubMetrics.incrementUserRegister();
        skillHubMetrics.recordLocalLogin(true);
        skillHubMetrics.incrementSkillPublish("global", "PENDING_REVIEW");

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
            .doesNotContain("prometheus")
            .doesNotContain("metrics");
        assertThat(meterRegistry.get("skillhub.user.register").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("skillhub.auth.login")
            .tag("method", "local")
            .tag("result", "success")
            .counter()
            .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("skillhub.skill.publish")
            .tag("namespace", "global")
            .tag("status", "PENDING_REVIEW")
            .counter()
            .count()).isEqualTo(1.0d);
    }
}
