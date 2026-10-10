package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Exhaustive result of the pure identity correlation stage. */
public sealed interface IdentityLoginResolution
        permits IdentityLoginResolution.Matched,
                IdentityLoginResolution.ProvisionNew,
                IdentityLoginResolution.Denied,
                IdentityLoginResolution.Conflict {

    record Matched(IdentityCorrelationStage stage, IdentityCorrelationTarget target)
            implements IdentityLoginResolution {
        public Matched {
            Objects.requireNonNull(stage, "correlation stage must not be null");
            Objects.requireNonNull(target, "correlation target must not be null");
        }
    }

    record ProvisionNew() implements IdentityLoginResolution {
    }

    record Denied(IdentityLoginDenialReason reason) implements IdentityLoginResolution {
        public Denied {
            Objects.requireNonNull(reason, "denial reason must not be null");
        }
    }

    record Conflict(IdentityCorrelationStage stage, int candidateCount) implements IdentityLoginResolution {
        public Conflict {
            Objects.requireNonNull(stage, "correlation stage must not be null");
            if (candidateCount < 2) {
                throw new IllegalArgumentException("conflict candidate count must be at least two");
            }
        }
    }
}
