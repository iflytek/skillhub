package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record LoginConnectionRevisionCreateRequest(
        @NotEmpty Map<String, Object> configuration,
        @Size(min = 1, max = 4096) String clientSecret
) {

    @Override
    public String toString() {
        return "LoginConnectionRevisionCreateRequest[configuration=<redacted>"
                + ", clientSecret=" + (clientSecret == null ? "unchanged" : "<redacted>") + "]";
    }
}
