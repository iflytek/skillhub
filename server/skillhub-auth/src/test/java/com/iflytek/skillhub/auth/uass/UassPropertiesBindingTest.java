package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class UassPropertiesBindingTest {

    @Test
    void defaults_matchExpectedUassConfiguration() {
        UassProperties properties = bind(Map.of());

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getBaseUrl()).isEmpty();
        assertThat(properties.getClientId()).isEmpty();
        assertThat(properties.getClientSecret()).isEmpty();
        assertThat(properties.getCallbackPath()).isEqualTo("/api/v1/auth/uass/callback");
        assertThat(properties.getStateTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getCacheMode()).isEqualTo(UassProperties.CacheMode.AUTO);
    }

    @Test
    void environmentVariables_overrideUassConfiguration() {
        UassProperties properties = bind(Map.of(
                "SKILLHUB_AUTH_UASS_ENABLED", "true",
                "SKILLHUB_AUTH_UASS_BASE_URL", " https://uass.internal ",
                "SKILLHUB_AUTH_UASS_CLIENT_ID", " skillhub-web ",
                "SKILLHUB_AUTH_UASS_CLIENT_SECRET", " secret-value ",
                "SKILLHUB_AUTH_UASS_CALLBACK_PATH", " /custom/uass/callback ",
                "SKILLHUB_AUTH_UASS_STATE_TTL", "PT7M",
                "SKILLHUB_AUTH_UASS_CACHE_MODE", "local"
        ));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBaseUrl()).isEqualTo("https://uass.internal");
        assertThat(properties.getClientId()).isEqualTo("skillhub-web");
        assertThat(properties.getClientSecret()).isEqualTo("secret-value");
        assertThat(properties.getCallbackPath()).isEqualTo("/custom/uass/callback");
        assertThat(properties.getStateTtl()).isEqualTo(Duration.ofMinutes(7));
        assertThat(properties.getCacheMode()).isEqualTo(UassProperties.CacheMode.LOCAL);
    }

    private UassProperties bind(Map<String, Object> envVars) {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env", envVars));
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment)
                .bind("skillhub.auth.uass", UassProperties.class)
                .orElseGet(UassProperties::new);
    }
}
