package com.iflytek.skillhub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

class SkillPublishPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsAllowedFileExtensionsFromEnvironmentStyleProperty() {
        contextRunner
                .withInitializer((context) -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource(
                                "test-env",
                                Map.of("SKILLHUB_PUBLISH_ALLOWED_FILE_EXTENSIONS", ".docx,.xsd,.pptx")
                        )
                ))
                .run((context) -> {
                    SkillPublishProperties properties = context.getBean(SkillPublishProperties.class);

                    assertThat(properties.getAllowedFileExtensions())
                            .containsExactly(".docx", ".xsd", ".pptx");
                });
    }

    @Configuration
    @EnableConfigurationProperties(SkillPublishProperties.class)
    static class TestConfig {
    }
}
