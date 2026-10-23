package com.iflytek.skillhub.domain.authoring.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScriptEnvironmentPolicyTest {
    @Test void emptyDeploymentDeniesRequestedVariable() {
        var policy = new ScriptEnvironmentPolicy(List::of);
        assertThat(policy.authorize(null)).isEmpty();
        assertThat(policy.authorize(List.of())).isEmpty();
        assertThatThrownBy(() -> policy.authorize(List.of("AUTHORING_DEMO")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsOnlySelectedSubsetAndDeduplicates() {
        var policy = new ScriptEnvironmentPolicy(() -> List.of("AUTHORING_DEMO", "AUTHORING_OTHER"));
        assertThat(policy.authorize(List.of("AUTHORING_DEMO", "AUTHORING_DEMO")))
                .containsExactly("AUTHORING_DEMO");
        assertThat(policy.authorize(List.of())).isEmpty();
    }
    @Test void mixedSelectionIsRejectedAsAWhole() {
        var policy = new ScriptEnvironmentPolicy(() -> List.of("AUTHORING_DEMO"));
        assertThatThrownBy(() -> policy.authorize(List.of("AUTHORING_DEMO", "AUTHORING_OTHER")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void malformedAndNonStringNamesCannotCoerceOrMatch() {
        var policy = new ScriptEnvironmentPolicy(() -> List.of("AUTHORING_DEMO"));
        for (Object value : List.of(42, Map.of("name", "AUTHORING_DEMO"), "authoring_demo", " AUTHORING_DEMO", "AUTHORING_DEMO ", "A-B", "A".repeat(65))) {
            assertThatThrownBy(() -> policy.authorize(List.of(value))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> policy.authorize(Arrays.asList((Object) null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.authorize("AUTHORING_DEMO")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reservedNamesCannotBeAuthorizedByDeployment() {
        for (String reserved : List.of("PATH", "HOME", "LANG", "SYSTEMROOT", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP", "Path", "path")) {
            assertThatThrownBy(() -> new ScriptEnvironmentPolicy(() -> List.of(reserved)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void malformedDeploymentFailsClosed() {
        assertThatThrownBy(() -> new ScriptEnvironmentPolicy(() -> List.of("bad-name")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ScriptEnvironmentPolicy(() -> Arrays.asList((String) null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new ScriptEnvironmentPolicy(() -> null).authorize(null)).isEmpty();
    }
    @Test void effectiveRevocationAppliesToPreviouslyValidSelection() {
        var current = new AtomicReference<>(List.of("AUTHORING_DEMO"));
        var policy = new ScriptEnvironmentPolicy(current::get);
        var selection = List.of("AUTHORING_DEMO");
        assertThat(policy.authorize(selection)).containsExactly("AUTHORING_DEMO");
        current.set(List.of());
        assertThatThrownBy(() -> policy.authorize(selection)).isInstanceOf(IllegalArgumentException.class);
        current.set(List.of("PATH"));
        assertThatThrownBy(() -> policy.authorize(null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void configValidationUsesCurrentPolicyWithoutDisclosingValues() {
        var current = new AtomicReference<>(List.of("AUTHORING_DEMO"));
        var validator = new RuntimeBindingValidator(new ScriptEnvironmentPolicy(current::get));
        var config = Map.<String, Object>of("interpreter", "sh", "envAllowlist", List.of("AUTHORING_DEMO"));
        assertThat(validator.validate("local-script", config, null, null)).isEmpty();
        current.set(List.of());
        assertThat(validator.validate("local-script", config, null, null))
                .extracting(finding -> finding.ruleCode()).containsExactly("ENV_ALLOWLIST_FORBIDDEN");
    }
}
