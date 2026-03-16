package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.security.scanner")
public class SkillScannerProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:8000";
    private String healthPath = "/health";
    private String scanPath = "/scan-upload";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 300000;
    private int retryMaxAttempts = 3;
    private String mode = "local"; // local | upload

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getHealthPath() { return healthPath; }
    public void setHealthPath(String healthPath) { this.healthPath = healthPath; }

    public String getScanPath() { return scanPath; }
    public void setScanPath(String scanPath) { this.scanPath = scanPath; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
