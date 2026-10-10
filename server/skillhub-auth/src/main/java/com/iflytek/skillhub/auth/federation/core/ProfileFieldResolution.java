package com.iflytek.skillhub.auth.federation.core;

import java.util.Objects;

/** Result of evaluating one field update without performing persistence. */
public sealed interface ProfileFieldResolution
        permits ProfileFieldResolution.Applied,
        ProfileFieldResolution.Preserved,
        ProfileFieldResolution.Rejected {

    record Applied(ProfileFieldState state) implements ProfileFieldResolution {
        public Applied {
            Objects.requireNonNull(state, "state");
        }
    }

    record Preserved(
            ProfileFieldState state,
            ProfileFieldResolutionReason reason
    ) implements ProfileFieldResolution {
        public Preserved {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Rejected(ProfileFieldResolutionReason reason) implements ProfileFieldResolution {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
