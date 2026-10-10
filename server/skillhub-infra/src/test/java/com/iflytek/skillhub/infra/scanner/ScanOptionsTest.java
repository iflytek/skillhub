package com.iflytek.skillhub.infra.scanner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanOptionsTest {

    @Test
    void disabled_usesSafeConsensusAndBalancedPolicyDefaults() {
        ScanOptions options = ScanOptions.disabled();

        assertThat(options.llmConsensusRuns()).isEqualTo(1);
        assertThat(options.policyPreset()).isEqualTo("balanced");
    }

    @Test
    void rejectsInvalidConsensusRuns() {
        assertThatThrownBy(() -> new ScanOptions(
                false, false, "anthropic", 0, "balanced", false, false, "", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("llmConsensusRuns");
    }

    @Test
    void rejectsUnknownPolicyPreset() {
        assertThatThrownBy(() -> new ScanOptions(
                false, false, "anthropic", 1, "custom", false, false, "", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyPreset");
    }

    @Test
    void rejectsUnknownLlmProvider() {
        assertThatThrownBy(() -> new ScanOptions(
                false, false, "openai&unexpected=value", 1, "balanced", false, false, "", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("llmProvider");
    }

    @Test
    void acceptsDocumentedAzureLlmProvider() {
        ScanOptions options = new ScanOptions(
                false, true, "azure", 1, "balanced", false, false, "", false, false);

        assertThat(options.llmProvider()).isEqualTo("azure-openai");
    }
}
