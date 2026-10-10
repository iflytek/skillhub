package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

class BuiltinSkillPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void enabledDefaultsToTrue() {
        contextRunner.run((context) -> {
            BuiltinSkillProperties properties = context.getBean(BuiltinSkillProperties.class);

            assertThat(properties.isEnabled()).isTrue();
        });
    }

    @Test
    void bindsEnabledFromEnvironmentStyleProperty() {
        contextRunner
                .withInitializer((context) -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource(
                                "test-env",
                                Map.of("SKILLHUB_BUILTIN_SKILLS_ENABLED", "false")
                        )
                ))
                .run((context) -> {
                    BuiltinSkillProperties properties = context.getBean(BuiltinSkillProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                });
    }

    @Configuration
    @EnableConfigurationProperties(BuiltinSkillProperties.class)
    static class TestConfig {
    }
}
