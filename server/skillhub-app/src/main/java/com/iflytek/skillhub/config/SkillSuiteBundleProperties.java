package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Runtime controls for staged Suite Bundle previews and execution rollout. */
@Component
@ConfigurationProperties(prefix = "skillhub.suite.bundle")
public class SkillSuiteBundleProperties {

    private Duration previewTtl = Duration.ofMinutes(30);
    private boolean confirmationEnabled;

    public Duration getPreviewTtl() {
        return previewTtl;
    }

    public void setPreviewTtl(Duration previewTtl) {
        if (previewTtl == null || previewTtl.isZero() || previewTtl.isNegative()) {
            throw new IllegalArgumentException("skillhub.suite.bundle.preview-ttl must be positive");
        }
        this.previewTtl = previewTtl;
    }

    public boolean isConfirmationEnabled() {
        return confirmationEnabled;
    }

    public void setConfirmationEnabled(boolean confirmationEnabled) {
        this.confirmationEnabled = confirmationEnabled;
    }
}
