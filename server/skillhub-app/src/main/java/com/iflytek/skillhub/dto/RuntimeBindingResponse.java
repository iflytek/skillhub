package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Runtime binding as seen by the authoring UI. */
public record RuntimeBindingResponse(
        String agentType,
        Map<String, Object> config,
        List<String> toolAllowlist,
        List<Map<String, Object>> mcpServers,
        Instant updatedAt
) {
}
