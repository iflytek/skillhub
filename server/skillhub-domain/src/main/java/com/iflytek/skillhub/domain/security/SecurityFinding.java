package com.iflytek.skillhub.domain.security;

import java.util.Map;

public record SecurityFinding(
        String ruleId,
        String severity,
        String category,
        String title,
        String message,
        String filePath,
        Integer lineNumber,
        String codeSnippet,
        String remediation,
        String analyzer,
        Map<String, Object> metadata
) {

    public SecurityFinding(String ruleId, String severity, String category,
                           String title, String message, String filePath,
                           Integer lineNumber, String codeSnippet) {
        this(ruleId, severity, category, title, message, filePath,
                lineNumber, codeSnippet, null, null, Map.of());
    }
}
