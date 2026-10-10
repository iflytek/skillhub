package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class RateLimitPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void enabledByDefaultAndNoOverrides() {
        RateLimitProperties properties = new RateLimitProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getCategories()).isEmpty();
    }

    @Test
    void fallsBackToAnnotationDefaultsWhenCategoryUnset() {
        RateLimitProperties properties = new RateLimitProperties();

        assertThat(properties.authenticatedFor("search", 60)).isEqualTo(60);
        assertThat(properties.anonymousFor("search", 20)).isEqualTo(20);
        assertThat(properties.windowSecondsFor("search", 60)).isEqualTo(60);
    }

    @Test
    void overridesOnlyTheFieldsThatAreSet() {
        RateLimitProperties.CategoryLimit search = new RateLimitProperties.CategoryLimit();
        search.setAuthenticated(120);
        // anonymous and windowSeconds intentionally left null

        RateLimitProperties properties = new RateLimitProperties();
        properties.getCategories().put("search", search);

        assertThat(properties.authenticatedFor("search", 60)).isEqualTo(120);
        assertThat(properties.anonymousFor("search", 20)).isEqualTo(20);
        assertThat(properties.windowSecondsFor("search", 60)).isEqualTo(60);
    }

    @Test
    void overrideAppliesPerCategoryOnly() {
        RateLimitProperties.CategoryLimit publish = new RateLimitProperties.CategoryLimit();
        publish.setAuthenticated(5);
        publish.setWindowSeconds(3600);

        RateLimitProperties properties = new RateLimitProperties();
        properties.getCategories().put("publish", publish);

        assertThat(properties.authenticatedFor("publish", 10)).isEqualTo(5);
        assertThat(properties.windowSecondsFor("publish", 60)).isEqualTo(3600);
        // A different category is unaffected.
        assertThat(properties.authenticatedFor("download", 120)).isEqualTo(120);
    }

    @Test
    void bindsDocumentedCategoryOverrideFromEnvironmentVariable() {
        // The *-systemEnvironment suffix activates Spring Boot's environment-variable name adaptation.
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("test-systemEnvironment", Map.of(
                                "SKILLHUB_RATELIMIT_CATEGORIES_SEARCH_AUTHENTICATED", "120"))))
                .run(context -> assertThat(context.getBean(RateLimitProperties.class)
                        .authenticatedFor("search", 60)).isEqualTo(120));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class TestConfiguration {
    }
}
