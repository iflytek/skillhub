package com.iflytek.skillhub.infra.scanner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillScannerApiResponse(
        @JsonProperty("scan_id") String scanId,
        @JsonProperty("is_safe") Boolean isSafe,
        @JsonProperty("max_severity") String maxSeverity,
        @JsonProperty("findings_count") Integer findingsCount,
        @JsonProperty("findings") List<Finding> findings,
        @JsonProperty("scan_duration_seconds") Double scanDurationSeconds
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finding(
            @JsonProperty("rule_id") String ruleId,
            @JsonProperty("severity") String severity,
            @JsonProperty("category") String category,
            @JsonProperty("title") String title,
            @JsonProperty("message") String message,
            @JsonProperty("location") Location location,
            @JsonProperty("code_snippet") String codeSnippet,
            @JsonProperty("remediation") String remediation,
            @JsonProperty("analyzer") String analyzer,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(
            @JsonProperty("file") String file,
            @JsonProperty("line") Integer line,
            @JsonProperty("column") Integer column
    ) {
    }
}
