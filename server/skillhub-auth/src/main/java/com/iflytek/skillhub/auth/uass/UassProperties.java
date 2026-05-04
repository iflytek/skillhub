package com.iflytek.skillhub.auth.uass;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
    private String mockLoginBaseUrl = "";
    private Duration stateTtl = Duration.ofMinutes(10);
    private CacheMode cacheMode = CacheMode.AUTO;
    private List<AdminUserConfig> adminUsers = List.of();

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

    public String getMockLoginBaseUrl() {
        return mockLoginBaseUrl;
    }

    public void setMockLoginBaseUrl(String mockLoginBaseUrl) {
        this.mockLoginBaseUrl = normalize(mockLoginBaseUrl);
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

    public List<AdminUserConfig> getAdminUsers() {
        return adminUsers;
    }

    public void setAdminUsers(List<AdminUserConfig> adminUsers) {
        this.adminUsers = adminUsers == null ? List.of() : List.copyOf(adminUsers);
    }

    public boolean isBootstrapAdminUssId(String ussId) {
        String normalizedUssId = normalize(ussId);
        if (!StringUtils.hasText(normalizedUssId)) {
            return false;
        }
        return adminUsers.stream().anyMatch(config -> normalizedUssId.equals(config.getUssId()));
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    public static class AdminUserConfig {
        private String ussId = "";

        public String getUssId() {
            return normalize(ussId);
        }

        public void setUssId(String ussId) {
            this.ussId = ussId;
        }
    }

    public enum CacheMode {
        REDIS,
        LOCAL,
        AUTO
    }
}
