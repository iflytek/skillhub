package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The startup guard makes host-side script execution impossible to configure
 * by accident in production: inline mode outside local/dev/test fails fast.
 */
class AuthoringStartupGuardTest {

    private final AuthoringProperties properties = new AuthoringProperties();

    private AuthoringStartupGuard guard(MockEnvironment environment) {
        return new AuthoringStartupGuard(properties, environment);
    }

    @Test
    void inlineModeWithoutDevProfileFailsStartup() {
        properties.getLocalScript().setExecutionMode(
                AuthoringProperties.ScriptExecutionMode.INLINE);
        assertThatThrownBy(() -> guard(new MockEnvironment()).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("execution-mode=inline")
                .hasMessageContaining("docker");
    }

    @Test
    void inlineModeWithProdLikeProfileStillFails() {
        properties.getLocalScript().setExecutionMode(
                AuthoringProperties.ScriptExecutionMode.INLINE);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("redis-sentinel");
        assertThatThrownBy(() -> guard(environment).run(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inlineModeIsAllowedInLocalDevAndTestProfiles() {
        properties.getLocalScript().setExecutionMode(
                AuthoringProperties.ScriptExecutionMode.INLINE);
        for (String profile : new String[]{"local", "dev", "test"}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            assertThatCode(() -> guard(environment).run(null))
                    .as("profile " + profile)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void dockerModePassesEverywhere() {
        properties.getLocalScript().setExecutionMode(
                AuthoringProperties.ScriptExecutionMode.DOCKER);
        assertThatCode(() -> guard(new MockEnvironment()).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledLocalScriptPassesEverywhere() {
        properties.getLocalScript().setEnabled(false);
        properties.getLocalScript().setExecutionMode(
                AuthoringProperties.ScriptExecutionMode.INLINE);
        assertThatCode(() -> guard(new MockEnvironment()).run(null))
                .doesNotThrowAnyException();
    }
}
