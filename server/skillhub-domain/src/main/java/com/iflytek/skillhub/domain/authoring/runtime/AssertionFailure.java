package com.iflytek.skillhub.domain.authoring.runtime;

import com.iflytek.skillhub.domain.authoring.spec.AssertionSpec;

/**
 * One assertion that did not hold, with a human-readable reason.
 */
public record AssertionFailure(AssertionSpec assertion, String reason) {

    public String describe() {
        return "assertion '" + assertion.type() + "' failed: " + reason;
    }
}
