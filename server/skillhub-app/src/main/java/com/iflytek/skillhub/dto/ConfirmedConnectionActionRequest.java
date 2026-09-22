package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.AssertTrue;

/** Explicit acknowledgement for an action that changes the authentication data plane. */
public record ConfirmedConnectionActionRequest(
        @AssertTrue(message = "{error.loginConnection.confirmation.required}") boolean confirmed
) {
}
