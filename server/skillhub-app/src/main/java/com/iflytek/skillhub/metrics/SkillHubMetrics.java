package com.iflytek.skillhub.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SkillHubMetrics {

    private final MeterRegistry meterRegistry;

    public SkillHubMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementUserRegister() {
        meterRegistry.counter("skillhub.user.register").increment();
    }

    public void recordLocalLogin(boolean success) {
        meterRegistry.counter(
            "skillhub.auth.login",
            "method", "local",
            "result", success ? "success" : "failure"
        ).increment();
    }

    public void incrementSkillPublish(String namespace, String status) {
        meterRegistry.counter(
            "skillhub.skill.publish",
            "namespace", namespace,
            "status", status
        ).increment();
    }
}
