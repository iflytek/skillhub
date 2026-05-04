package com.iflytek.skillhub.config;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class TestProfileBindingTest {

    @Test
    void testProfileClearsMainRuntimeDatasourceWithoutReintroducingH2Settings() throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : java.util.List.of("application-test.yml", "application.yml")) {
            for (var propertySource : loader.load(resourceName, new ClassPathResource(resourceName))) {
                environment.getPropertySources().addLast(propertySource);
            }
        }
        ConfigurationPropertySources.attach(environment);

        assertThat(environment.getProperty("spring.datasource.url", "")).isEmpty();
        assertThat(environment.getProperty("spring.datasource.driver-class-name", "")).isEmpty();
        assertThat(environment.getProperty("spring.datasource.username", "")).isEmpty();
        assertThat(environment.getProperty("spring.datasource.password", "")).isEmpty();
        assertThat(environment.getProperty("spring.jpa.database-platform", "")).isEmpty();
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.dialect", "")).isEmpty();
        assertThat(environment.getProperty("spring.datasource.generate-unique-name")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create");
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
    }
}
