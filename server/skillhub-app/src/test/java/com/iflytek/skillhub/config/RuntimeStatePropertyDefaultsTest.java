package com.iflytek.skillhub.config;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeStatePropertyDefaultsTest {

    @Test
    void resolveOverrides_returnsMemoryBackedRuntimeDefaults() {
        StandardEnvironment environment = environmentWith(Map.of(RuntimeStatePropertyDefaults.PROVIDER_PROPERTY, "memory"));

        Map<String, Object> overrides = RuntimeStatePropertyDefaults.resolveOverrides(environment);

        assertThat(overrides)
                .containsEntry("spring.session.store-type", "none")
                .containsEntry("skillhub.ratelimit.mode", "memory")
                .containsEntry("skillhub.auth.failure-throttle.mode", "memory")
                .containsEntry("skillhub.auth.uass.cache-mode", "local");
        assertThat(overrides.get("spring.autoconfigure.exclude"))
                .isEqualTo(RuntimeStatePropertyDefaults.memoryAutoConfigurationExcludes());
    }

    @Test
    void resolveOverrides_returnsRedisBackedRuntimeDefaults() {
        StandardEnvironment environment = environmentWith(Map.of(RuntimeStatePropertyDefaults.PROVIDER_PROPERTY, "redis"));

        Map<String, Object> overrides = RuntimeStatePropertyDefaults.resolveOverrides(environment);

        assertThat(overrides)
                .containsEntry("spring.session.store-type", "redis")
                .containsEntry("skillhub.ratelimit.mode", "redis")
                .containsEntry("skillhub.auth.failure-throttle.mode", "redis")
                .containsEntry("skillhub.auth.uass.cache-mode", "redis");
        assertThat(overrides.get("spring.autoconfigure.exclude")).isEqualTo(List.of());
    }

    @Test
    void resolveOverrides_returnsEmptyMapWhenProviderIsNotConfigured() {
        StandardEnvironment environment = environmentWith(Map.of());

        assertThat(RuntimeStatePropertyDefaults.resolveOverrides(environment)).isEmpty();
    }

    @Test
    void resolveOverrides_rejectsUnsupportedProviderValues() {
        StandardEnvironment environment = environmentWith(Map.of(RuntimeStatePropertyDefaults.PROVIDER_PROPERTY, "filesystem"));

        assertThatThrownBy(() -> RuntimeStatePropertyDefaults.resolveOverrides(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skillhub.runtime.state.provider")
                .hasMessageContaining("memory or redis");
    }

    private StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }
}
