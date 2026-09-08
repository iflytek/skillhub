package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record LoginConnectionCreateRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 64) String adapterKey,
        @NotEmpty Map<String, Object> configuration,
        @Size(min = 1, max = 4096) String clientSecret,
        boolean verifiedEmailCorrelationEnabled,
        boolean jitProvisioningEnabled
) {

    @Override
    public String toString() {
        return "LoginConnectionCreateRequest[displayName=" + displayName
                + ", adapterKey=" + adapterKey
                + ", configuration=<redacted>, clientSecret="
                + (clientSecret == null ? "none" : "<redacted>")
                + ", verifiedEmailCorrelationEnabled=" + verifiedEmailCorrelationEnabled
                + ", jitProvisioningEnabled=" + jitProvisioningEnabled + "]";
    }
}
