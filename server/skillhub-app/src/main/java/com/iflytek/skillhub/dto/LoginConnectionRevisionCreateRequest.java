package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginConnectionRevisionCreateRequest(
        @NotBlank @Size(max = 64) String expectedRevisionId,
        @NotBlank @Size(max = 512) String issuer,
        @NotBlank @Size(max = 256) String clientId
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported login connection configuration field");
    }
}
