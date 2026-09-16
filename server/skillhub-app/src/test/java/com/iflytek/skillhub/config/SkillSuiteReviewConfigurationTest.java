package com.iflytek.skillhub.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSuiteReviewConfigurationTest {

    @Test
    void defaultConfigurationKeepsSuiteReviewWritesFailClosed() throws IOException {
        ConfigurableEnvironment environment = loadApplicationEnvironment(
                List.of("application.yml"), Map.of());

        assertThat(environment.getProperty("skillhub.suite.review-writes-enabled", Boolean.class))
                .isFalse();
    }

    @Test
    void localProfileEnablesSuiteReviewWrites() throws IOException {
        ConfigurableEnvironment environment = loadApplicationEnvironment(
                List.of("application-local.yml", "application.yml"), Map.of());

        assertThat(environment.getProperty("skillhub.suite.review-writes-enabled", Boolean.class))
                .isTrue();
    }

    @Test
    void localProfileCanExplicitlyDisableSuiteReviewWrites() throws IOException {
        ConfigurableEnvironment environment = loadApplicationEnvironment(
                List.of("application-local.yml", "application.yml"),
                Map.of("SKILLHUB_SUITE_REVIEW_WRITES_ENABLED", "false"));

        assertThat(environment.getProperty("skillhub.suite.review-writes-enabled", Boolean.class))
                .isFalse();
    }

    private ConfigurableEnvironment loadApplicationEnvironment(List<String> resourceNames,
                                                               Map<String, Object> environmentVariables)
            throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-env", environmentVariables));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : resourceNames) {
            List<org.springframework.core.env.PropertySource<?>> sources = loader.load(
                    resourceName,
                    new ClassPathResource(resourceName));
            sources.forEach(environment.getPropertySources()::addLast);
        }
        return environment;
    }
}
