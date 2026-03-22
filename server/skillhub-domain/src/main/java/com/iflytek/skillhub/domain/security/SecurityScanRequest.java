package com.iflytek.skillhub.domain.security;

import java.util.Map;

public record SecurityScanRequest(
        String scanId,
        Long skillVersionId,
        String skillPackagePath,
        Map<String, String> scanOptions
) {
}
