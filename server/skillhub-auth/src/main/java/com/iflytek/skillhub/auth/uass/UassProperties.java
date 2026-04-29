package com.iflytek.skillhub.auth.uass;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.uass")
public class UassProperties {

    private boolean enabled = false;
    private String baseUrl = "";
    private String clientId = "";
    private String clientSecret = "";
    private String callbackPath = "/api/v1/auth/uass/callback";
    private Duration stateTtl = Duration.ofMinutes(10);
    private CacheMode cacheMode = CacheMode.AUTO;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = normalize(baseUrl);
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = normalize(clientId);
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = normalize(clientSecret);
    }

    public String getCallbackPath() {
        return callbackPath;
    }

    public void setCallbackPath(String callbackPath) {
        this.callbackPath = StringUtils.hasText(callbackPath)
                ? callbackPath.trim()
                : "/api/v1/auth/uass/callback";
    }

    public Duration getStateTtl() {
        return stateTtl;
    }

    public void setStateTtl(Duration stateTtl) {
        this.stateTtl = stateTtl == null ? Duration.ofMinutes(10) : stateTtl;
    }

    public CacheMode getCacheMode() {
        return cacheMode;
    }

    public void setCacheMode(CacheMode cacheMode) {
        this.cacheMode = cacheMode == null ? CacheMode.AUTO : cacheMode;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public enum CacheMode {
        REDIS,
        LOCAL,
        AUTO
    }
}
