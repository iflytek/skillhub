package com.iflytek.skillhub.config;

import com.iflytek.skillhub.bootstrap.BootstrapAdminProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapAdminPropertiesBindingTest {

    @Test
    void defaultConfig_keepsBootstrapAdminDisabled() throws IOException {
        BootstrapAdminProperties properties = bindProperties(
                List.of("application.yml"),
                Map.of()
        );

        assertFalse(properties.isEnabled());
    }

    @Test
    void localProfile_enablesBootstrapAdminByDefault() throws IOException {
        BootstrapAdminProperties properties = bindProperties(
                List.of("application-local.yml", "application.yml"),
                Map.of()
        );

        assertTrue(properties.isEnabled());
    }

    @Test
    void localProfile_allowsEnvironmentVariablesToDisableBootstrapAdmin() throws IOException {
        BootstrapAdminProperties properties = bindProperties(
                List.of("application-local.yml", "application.yml"),
                Map.of("BOOTSTRAP_ADMIN_ENABLED", "false")
        );

        assertFalse(properties.isEnabled());
    }

    private BootstrapAdminProperties bindProperties(List<String> resourceNames,
                                                    Map<String, Object> envVars) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("test-env", envVars));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : resourceNames) {
            List<org.springframework.core.env.PropertySource<?>> propertySources = loader.load(
                    resourceName,
                    new ClassPathResource(resourceName)
            );
            for (org.springframework.core.env.PropertySource<?> propertySource : propertySources) {
                environment.getPropertySources().addLast(propertySource);
            }
        }
        ConfigurationPropertySources.attach(environment);

        return Binder.get(environment)
                .bind("skillhub.bootstrap.admin", BootstrapAdminProperties.class)
                .orElseThrow(() -> new IllegalStateException("Failed to bind bootstrap admin properties"));
    }
}
