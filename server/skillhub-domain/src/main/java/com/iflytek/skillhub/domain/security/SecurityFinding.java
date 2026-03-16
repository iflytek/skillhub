package com.iflytek.skillhub.domain.security;

public record SecurityFinding(
    String ruleId,
    String severity,
    String category,
    String title,
    String message,
    String filePath,
    Integer lineNumber,
    String codeSnippet
) {}
