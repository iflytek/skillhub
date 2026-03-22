package com.iflytek.skillhub.infra.scanner;

public record ScanOptions(
        boolean useBehavioral,
        boolean useLlm,
        String llmProvider,
        boolean enableMeta,
        boolean useAidefense,
        String aidefenseApiKey,
        boolean useVirusTotal,
        boolean useTrigger
) {

    public static ScanOptions disabled() {
        return new ScanOptions(false, false, "anthropic", false, false, "", false, false);
    }
}
