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
    private String mode = "local";
    private Analyzers analyzers = new Analyzers();
    private Policy policy = new Policy();

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
        this.baseUrl = baseUrl;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public String getScanPath() {
        return scanPath;
    }

    public void setScanPath(String scanPath) {
        this.scanPath = scanPath;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Analyzers getAnalyzers() {
        return analyzers;
    }

    public void setAnalyzers(Analyzers analyzers) {
        this.analyzers = analyzers;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public static class Analyzers {
        private boolean behavioral = false;
        private boolean llm = false;
        private String llmProvider = "anthropic";
        private int llmConsensusRuns = 1;
        private boolean meta = false;
        private boolean aiDefense = false;
        private String aiDefenseApiKey = "";
        private boolean virusTotal = false;
        private boolean trigger = false;

        public boolean isBehavioral() {
            return behavioral;
        }

        public void setBehavioral(boolean behavioral) {
            this.behavioral = behavioral;
        }

        public boolean isLlm() {
            return llm;
        }

        public void setLlm(boolean llm) {
            this.llm = llm;
        }

        public String getLlmProvider() {
            return llmProvider;
        }

        public void setLlmProvider(String llmProvider) {
            this.llmProvider = llmProvider;
        }

        public int getLlmConsensusRuns() {
            return llmConsensusRuns;
        }

        public void setLlmConsensusRuns(int llmConsensusRuns) {
            this.llmConsensusRuns = llmConsensusRuns;
        }

        public boolean isMeta() {
            return meta;
        }

        public void setMeta(boolean meta) {
            this.meta = meta;
        }

        public boolean isAiDefense() {
            return aiDefense;
        }

        public void setAiDefense(boolean aiDefense) {
            this.aiDefense = aiDefense;
        }

        public String getAiDefenseApiKey() {
            return aiDefenseApiKey;
        }

        public void setAiDefenseApiKey(String aiDefenseApiKey) {
            this.aiDefenseApiKey = aiDefenseApiKey;
        }

        public boolean isVirusTotal() {
            return virusTotal;
        }

        public void setVirusTotal(boolean virusTotal) {
            this.virusTotal = virusTotal;
        }

        public boolean isTrigger() {
            return trigger;
        }

        public void setTrigger(boolean trigger) {
            this.trigger = trigger;
        }
    }

    public static class Policy {
        private String preset = "balanced";
        private String customPolicyPath = "";
        private String failOnSeverity = "high";

        public String getPreset() {
            return preset;
        }

        public void setPreset(String preset) {
            this.preset = preset;
        }

        public String getCustomPolicyPath() {
            return customPolicyPath;
        }

        public void setCustomPolicyPath(String customPolicyPath) {
            this.customPolicyPath = customPolicyPath;
        }

        public String getFailOnSeverity() {
            return failOnSeverity;
        }

        public void setFailOnSeverity(String failOnSeverity) {
            this.failOnSeverity = failOnSeverity;
        }
    }
}
