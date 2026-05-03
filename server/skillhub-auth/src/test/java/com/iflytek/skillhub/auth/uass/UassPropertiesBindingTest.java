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

    @Test
    void setters_fallBackToDefaultsForBlankAndNullValues() {
        UassProperties properties = new UassProperties();
        properties.setCallbackPath(" ");
        properties.setStateTtl(null);
        properties.setCacheMode(null);
        properties.setAdminUsers(null);

        UassProperties.AdminUserConfig adminUser = new UassProperties.AdminUserConfig();
        adminUser.setUssId(" U-1 ");
        adminUser.setRoles(null);
        properties.setAdminUsers(java.util.List.of(adminUser));

        assertThat(properties.getCallbackPath()).isEqualTo("/api/v1/auth/uass/callback");
        assertThat(properties.getStateTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getCacheMode()).isEqualTo(UassProperties.CacheMode.AUTO);
        assertThat(adminUser.getRoles()).containsExactly("USER_ADMIN");
        assertThat(properties.rolesForUssId(" ")).isEmpty();
        assertThat(properties.rolesForUssId("U-1")).containsExactly("USER_ADMIN");
    }

    @Test
    void rolesForUssId_normalizesAndFiltersConfiguredRoles() {
        UassProperties properties = new UassProperties();
        UassProperties.AdminUserConfig adminUser = new UassProperties.AdminUserConfig();
        adminUser.setUssId(" U-2 ");
        adminUser.setRoles(java.util.List.of(" auditor ", " ", "user_admin"));
        properties.setAdminUsers(java.util.List.of(adminUser));

        assertThat(adminUser.getUssId()).isEqualTo("U-2");
        assertThat(properties.rolesForUssId(" U-2 ")).containsExactly("AUDITOR", "USER_ADMIN");
        assertThat(properties.rolesForUssId("unknown")).isEmpty();
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
