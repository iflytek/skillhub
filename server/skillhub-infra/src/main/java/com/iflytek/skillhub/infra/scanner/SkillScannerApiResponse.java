package com.iflytek.skillhub.infra.scanner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillScannerApiResponse(
        @JsonProperty("scan_id") String scanId,
        @JsonProperty("skill_name") String skillName,
        @JsonProperty("is_safe") Boolean isSafe,
        @JsonProperty("max_severity") String maxSeverity,
        @JsonProperty("findings_count") Integer findingsCount,
        @JsonProperty("findings") List<Finding> findings,
        @JsonProperty("scan_duration_seconds") Double scanDurationSeconds,
        @JsonProperty("timestamp") String timestamp
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finding(
            @JsonProperty("id") String id,
            @JsonProperty("rule_id") String ruleId,
            @JsonProperty("severity") String severity,
            @JsonProperty("category") String category,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("file_path") String filePath,
            @JsonProperty("line_number") Integer lineNumber,
            @JsonProperty("snippet") String snippet,
            @JsonProperty("remediation") String remediation,
            @JsonProperty("analyzer") String analyzer,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
    }
}
