package com.iflytek.skillhub.ratelimit;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.ratelimit")
public class RateLimitProperties {

    private List<String> trustedProxies = Collections.emptyList();
    private GlobalIpLimit globalIpLimit = new GlobalIpLimit();

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies != null ? trustedProxies : Collections.emptyList();
    }

    public GlobalIpLimit getGlobalIpLimit() {
        return globalIpLimit;
    }

    public void setGlobalIpLimit(GlobalIpLimit globalIpLimit) {
        this.globalIpLimit = globalIpLimit;
    }

    public static class GlobalIpLimit {
        private boolean enabled = true;
        private int maxRequests = 300;
        private int windowSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
