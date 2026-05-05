package com.iflytek.skillhub.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillHubMetricsTest {

    private MeterRegistry meterRegistry;
    private SkillHubMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new SkillHubMetrics(meterRegistry);
    }

    @Test
    void incrementUserRegister_createsCounter() {
        metrics.incrementUserRegister();

        Counter counter = meterRegistry.find("skillhub.user.register").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordLocalLogin_success_createsCounterWithSuccessTag() {
        metrics.recordLocalLogin(true);

        Counter counter = meterRegistry.find("skillhub.auth.login")
                .tag("method", "local")
                .tag("result", "success")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordLocalLogin_failure_createsCounterWithFailureTag() {
        metrics.recordLocalLogin(false);

        Counter counter = meterRegistry.find("skillhub.auth.login")
                .tag("method", "local")
                .tag("result", "failure")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void incrementSkillPublish_createsCounterWithTags() {
        metrics.incrementSkillPublish("global", "published");

        Counter counter = meterRegistry.find("skillhub.skill.publish")
                .tag("namespace", "global")
                .tag("status", "published")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordDownloadDelivery_createsCounterWithTags() {
        metrics.recordDownloadDelivery("presigned", true);

        Counter counter = meterRegistry.find("skillhub.skill.download.delivery")
                .tag("mode", "presigned")
                .tag("fallback_bundle", "true")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordDownloadDelivery_withFalseFallback_createsCounter() {
        metrics.recordDownloadDelivery("stream", false);

        Counter counter = meterRegistry.find("skillhub.skill.download.delivery")
                .tag("mode", "stream")
                .tag("fallback_bundle", "false")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void incrementBundleMissingFallback_createsCounter() {
        metrics.incrementBundleMissingFallback();

        Counter counter = meterRegistry.find("skillhub.skill.download.bundle_missing_fallback").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void incrementRateLimitExceeded_createsCounter() {
        metrics.incrementRateLimitExceeded("download");

        Counter counter = meterRegistry.find("skillhub.ratelimit.exceeded")
                .tag("category", "download")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void incrementStorageAccessFailure_createsCounter() {
        metrics.incrementStorageAccessFailure("getObject");

        Counter counter = meterRegistry.find("skillhub.storage.failure")
                .tag("operation", "getObject")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
