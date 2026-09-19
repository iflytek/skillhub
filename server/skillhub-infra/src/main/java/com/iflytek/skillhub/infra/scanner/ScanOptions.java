package com.iflytek.skillhub.infra.scanner;

public record ScanOptions(
        boolean useBehavioral,
        boolean useLlm,
        String llmProvider,
        int llmConsensusRuns,
        String policyPreset,
        boolean enableMeta,
        boolean useAidefense,
        String aidefenseApiKey,
        boolean useVirusTotal,
        boolean useTrigger
) {

    public ScanOptions {
        if ("azure".equals(llmProvider)) {
            llmProvider = "azure-openai";
        }
        if (!"anthropic".equals(llmProvider)
                && !"openai".equals(llmProvider)
                && !"azure-openai".equals(llmProvider)) {
            throw new IllegalArgumentException("llmProvider must be anthropic, openai, or azure");
        }
        if (llmConsensusRuns < 1) {
            throw new IllegalArgumentException("llmConsensusRuns must be at least 1");
        }
        if (!"strict".equals(policyPreset)
                && !"balanced".equals(policyPreset)
                && !"permissive".equals(policyPreset)) {
            throw new IllegalArgumentException("policyPreset must be strict, balanced, or permissive");
        }
    }

    /** Backward-compatible constructor for callers that do not configure the new scanner options. */
    public ScanOptions(boolean useBehavioral,
                       boolean useLlm,
                       String llmProvider,
                       boolean enableMeta,
                       boolean useAidefense,
                       String aidefenseApiKey,
                       boolean useVirusTotal,
                       boolean useTrigger) {
        this(useBehavioral, useLlm, llmProvider, 1, "balanced", enableMeta,
                useAidefense, aidefenseApiKey, useVirusTotal, useTrigger);
    }

    public static ScanOptions disabled() {
        return new ScanOptions(false, false, "anthropic", 1, "balanced", false, false, "", false, false);
    }
}
