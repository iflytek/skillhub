package com.iflytek.skillhub.auth.connection.control;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Untrusted, protocol-neutral draft configuration accepted by the control-plane registry. */
public record LoginConnectionConfigurationInput(Map<String, Object> values) {

    public LoginConnectionConfigurationInput {
        Objects.requireNonNull(values, "values");
        values = Map.copyOf(new LinkedHashMap<>(values));
    }

    @Override
    public String toString() {
        return "LoginConnectionConfigurationInput[values=<redacted>]";
    }
}
