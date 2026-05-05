package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillScannerPropertiesTest {

    @Test
    void gettersAndSetters_workForMainProperties() {
        SkillScannerProperties props = new SkillScannerProperties();

        props.setEnabled(true);
        assertThat(props.isEnabled()).isTrue();

        props.setBaseUrl("http://scanner.test");
        assertThat(props.getBaseUrl()).isEqualTo("http://scanner.test");

        props.setHealthPath("/healthz");
        assertThat(props.getHealthPath()).isEqualTo("/healthz");

        props.setScanPath("/scan");
        assertThat(props.getScanPath()).isEqualTo("/scan");

        props.setConnectTimeoutMs(1000);
        assertThat(props.getConnectTimeoutMs()).isEqualTo(1000);

        props.setReadTimeoutMs(5000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(5000);

        props.setRetryMaxAttempts(5);
        assertThat(props.getRetryMaxAttempts()).isEqualTo(5);

        props.setMode("upload");
        assertThat(props.getMode()).isEqualTo("upload");
    }

    @Test
    void defaultValuesAreCorrect() {
        SkillScannerProperties props = new SkillScannerProperties();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getBaseUrl()).isEqualTo("http://localhost:8000");
        assertThat(props.getHealthPath()).isEqualTo("/health");
        assertThat(props.getScanPath()).isEqualTo("/scan-upload");
        assertThat(props.getConnectTimeoutMs()).isEqualTo(5000);
        assertThat(props.getReadTimeoutMs()).isEqualTo(300000);
        assertThat(props.getRetryMaxAttempts()).isEqualTo(3);
        assertThat(props.getMode()).isEqualTo("local");
    }

    @Test
    void analyzers_settersAndGetters_work() {
        SkillScannerProperties props = new SkillScannerProperties();
        SkillScannerProperties.Analyzers analyzers = new SkillScannerProperties.Analyzers();

        analyzers.setBehavioral(true);
        assertThat(analyzers.isBehavioral()).isTrue();

        analyzers.setLlm(true);
        assertThat(analyzers.isLlm()).isTrue();

        analyzers.setLlmProvider("openai");
        assertThat(analyzers.getLlmProvider()).isEqualTo("openai");

        analyzers.setLlmConsensusRuns(3);
        assertThat(analyzers.getLlmConsensusRuns()).isEqualTo(3);

        analyzers.setMeta(true);
        assertThat(analyzers.isMeta()).isTrue();

        analyzers.setAiDefense(true);
        assertThat(analyzers.isAiDefense()).isTrue();

        analyzers.setAiDefenseApiKey("secret-key");
        assertThat(analyzers.getAiDefenseApiKey()).isEqualTo("secret-key");

        analyzers.setVirusTotal(true);
        assertThat(analyzers.isVirusTotal()).isTrue();

        analyzers.setTrigger(true);
        assertThat(analyzers.isTrigger()).isTrue();

        props.setAnalyzers(analyzers);
        assertThat(props.getAnalyzers()).isSameAs(analyzers);
    }

    @Test
    void analyzers_defaultValuesAreCorrect() {
        SkillScannerProperties.Analyzers analyzers = new SkillScannerProperties.Analyzers();

        assertThat(analyzers.isBehavioral()).isFalse();
        assertThat(analyzers.isLlm()).isFalse();
        assertThat(analyzers.getLlmProvider()).isEqualTo("anthropic");
        assertThat(analyzers.getLlmConsensusRuns()).isEqualTo(1);
        assertThat(analyzers.isMeta()).isFalse();
        assertThat(analyzers.isAiDefense()).isFalse();
        assertThat(analyzers.getAiDefenseApiKey()).isEmpty();
        assertThat(analyzers.isVirusTotal()).isFalse();
        assertThat(analyzers.isTrigger()).isFalse();
    }

    @Test
    void policy_settersAndGetters_work() {
        SkillScannerProperties.Policy policy = new SkillScannerProperties.Policy();

        policy.setPreset("strict");
        assertThat(policy.getPreset()).isEqualTo("strict");

        policy.setCustomPolicyPath("/path/to/policy");
        assertThat(policy.getCustomPolicyPath()).isEqualTo("/path/to/policy");

        policy.setFailOnSeverity("critical");
        assertThat(policy.getFailOnSeverity()).isEqualTo("critical");

        SkillScannerProperties props = new SkillScannerProperties();
        props.setPolicy(policy);
        assertThat(props.getPolicy()).isSameAs(policy);
    }

    @Test
    void policy_defaultValuesAreCorrect() {
        SkillScannerProperties.Policy policy = new SkillScannerProperties.Policy();

        assertThat(policy.getPreset()).isEqualTo("balanced");
        assertThat(policy.getCustomPolicyPath()).isEmpty();
        assertThat(policy.getFailOnSeverity()).isEqualTo("high");
    }
}
