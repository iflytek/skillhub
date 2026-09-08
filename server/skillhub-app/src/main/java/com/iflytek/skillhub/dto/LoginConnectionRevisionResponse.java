package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.Map;

public record LoginConnectionRevisionResponse(
        String id,
        long revision,
        String adapterContractVersion,
        int configSchemaVersion,
        Map<String, Object> configuration,
        boolean verifiedEmailCorrelationEnabled,
        boolean jitProvisioningEnabled,
        Instant createdAt
) {
}
