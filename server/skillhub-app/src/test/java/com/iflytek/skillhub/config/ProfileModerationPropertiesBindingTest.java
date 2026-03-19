package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileModerationPropertiesBindingTest {

    @Test
    void defaultConfig_disablesMachineAndHumanReview() throws IOException {
        ProfileModerationProperties properties = bindProperties(
                List.of("application.yml"),
                Map.of()
        );

        assertFalse(properties.machineReview());
        assertFalse(properties.humanReview());
    }

    @Test
    void localProfile_enablesMachineAndHumanReviewByDefault() throws IOException {
        ProfileModerationProperties properties = bindProperties(
                List.of("application-local.yml", "application.yml"),
                Map.of()
        );

        assertTrue(properties.machineReview());
        assertTrue(properties.humanReview());
    }

    @Test
    void localProfile_allowsEnvironmentVariablesToOverrideDefaults() throws IOException {
        ProfileModerationProperties properties = bindProperties(
                List.of("application-local.yml", "application.yml"),
                Map.of(
                        "SKILLHUB_PROFILE_MACHINE_REVIEW_ENABLED", "false",
                        "SKILLHUB_PROFILE_HUMAN_REVIEW_ENABLED", "false"
                )
        );

        assertFalse(properties.machineReview());
        assertFalse(properties.humanReview());
    }

    private ProfileModerationProperties bindProperties(List<String> resourceNames,
                                                       Map<String, Object> envVars) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", envVars));

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
                .bind("skillhub.profile.moderation", ProfileModerationProperties.class)
                .orElseThrow(() -> new IllegalStateException("Failed to bind profile moderation properties"));
    }
}
