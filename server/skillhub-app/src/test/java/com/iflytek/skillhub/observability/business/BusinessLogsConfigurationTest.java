package com.iflytek.skillhub.observability.business;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessLogsConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BusinessLogsConfiguration.class)
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultOffCreatesNoProviderPipelineOrListener() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BusinessLogsPipeline.class);
            assertThat(context).doesNotHaveBean(BusinessEventListener.class);
            assertThat(context).doesNotHaveBean(io.opentelemetry.sdk.logs.SdkLoggerProvider.class);
        });
    }

    @Test
    void enabledRequiresExplicitEndpoint() {
        runner.withPropertyValues("skillhub.observability.business-logs.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://169.254.169.254/v1/logs", "http://metadata.google.internal/v1/logs",
            "http://user:password@localhost/v1/logs", "http://localhost/v1/logs?secret=x", "file:///v1/logs"})
    void invalidDestinationsFailWithoutEchoingInput(String endpoint) {
        var properties = new BusinessLogsProperties();
        properties.setEnabled(true);
        properties.setEndpoint(endpoint);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(endpoint).hasMessageNotContaining("password").hasMessageNotContaining("secret=x");
    }

    @Test
    void budgetsAndEventNamesAreValidated() {
        var properties = new BusinessLogsProperties();
        properties.setEnabled(true);
        properties.setEndpoint("http://localhost:4318/v1/logs");
        properties.setMaxQueueSize(1);
        properties.setMaxExportBatchSize(2);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setMaxExportBatchSize(1);
        properties.setEventAllowlist(java.util.Set.of("not-a-business-event"));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void headerEncodingIsSecretFriendlyAndErrorsNeverEchoSecretInput() {
        var properties = new BusinessLogsProperties();
        properties.setHeaders("Authorization=Bearer%20fake%2Bsecret,x-tenant=example");
        assertThat(properties.parsedHeaders()).containsEntry("Authorization", "Bearer fake+secret");
        properties.setHeaders("Authorization=fake-secret%0Ainjected");
        assertThatThrownBy(properties::parsedHeaders).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("fake-secret").hasMessageNotContaining("injected");
        properties.setHeaders(String.join(",", java.util.Collections.nCopies(17, "same=value")));
        assertThatThrownBy(properties::parsedHeaders).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyNamespaceFilterAndExplicitEmptyEventFilterBindWithoutDefaultDestination() {
        runner.withPropertyValues("skillhub.observability.business-logs.enabled=true",
                        "skillhub.observability.business-logs.endpoint=http://127.0.0.1:4318/v1/logs",
                        "skillhub.observability.business-logs.namespace-allowlist=",
                        "skillhub.observability.business-logs.event-allowlist=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BusinessLogsProperties.class).getNamespaceAllowlist()).isEmpty();
                    assertThat(context.getBean(BusinessLogsProperties.class).getEventAllowlist()).isEmpty();
                });
    }
}
