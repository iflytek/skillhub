package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginConnectionCreateRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 512) String issuer,
        @NotBlank @Size(max = 256) String clientId
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported login connection configuration field");
    }
}
