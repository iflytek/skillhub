package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginConnectionSecretRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Size(max = 4096) String clientSecret
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported login connection credential field");
    }

    @Override
    public String toString() {
        return "LoginConnectionSecretRequest[clientSecret=<redacted>]";
    }
}
