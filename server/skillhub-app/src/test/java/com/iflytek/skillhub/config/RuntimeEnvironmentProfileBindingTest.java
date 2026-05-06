package com.iflytek.skillhub.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeEnvironmentProfileBindingTest {

    @Test
    void devProfile_usesMysqlMemoryAndMysqlLikeDefaults() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-dev.yml", "application.yml"),
                Map.of()
        );
        applyRuntimeStateDefaults(environment);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://localhost:3306/skillhub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8&useUnicode=true");
        assertThat(environment.getProperty("skillhub.runtime.state.provider")).isEqualTo("memory");
        assertThat(environment.getProperty("skillhub.search.provider")).isEqualTo("mysql-like");
        assertThat(environment.getProperty("spring.session.store-type")).isEqualTo("none");
        assertThat(environment.getProperty("skillhub.storage.local.base-path"))
                .isEqualTo(System.getProperty("user.home") + "/.skillhub/dev/storage");
        assertThat(environment.getProperty("skillhub.auth.mock.enabled")).isEqualTo("true");
    }

    @Test
    void testProfile_usesMysqlRedisAndMysqlLikeDefaults() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-test.yml", "application.yml"),
                Map.of()
        );
        applyRuntimeStateDefaults(environment);

        assertThat(environment.getProperty("skillhub.runtime.state.provider")).isEqualTo("redis");
        assertThat(environment.getProperty("skillhub.search.provider")).isEqualTo("mysql-like");
        assertThat(environment.getProperty("spring.session.store-type")).isEqualTo("redis");
        assertThat(environment.getProperty("skillhub.storage.local.base-path"))
                .isEqualTo(System.getProperty("user.home") + "/.skillhub/test/storage");
        assertThat(environment.getProperty("skillhub.auth.mock.enabled")).isEqualTo("false");
    }

    @Test
    void prodProfile_usesMysqlRedisAndLocalFileIndexDefaults() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-prod.yml", "application.yml"),
                Map.of(
                        "PROD_DATASOURCE_URL", "jdbc:mysql://prod.internal:3306/skillhub",
                        "PROD_DATASOURCE_USERNAME", "prod_user",
                        "PROD_DATASOURCE_PASSWORD", "prod_password",
                        "SKILLHUB_PUBLIC_BASE_URL", "https://skillhub.example.com"
                )
        );
        applyRuntimeStateDefaults(environment);

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://prod.internal:3306/skillhub");
        assertThat(environment.getProperty("skillhub.runtime.state.provider")).isEqualTo("redis");
        assertThat(environment.getProperty("skillhub.search.provider")).isEqualTo("local-file-index");
        assertThat(environment.getProperty("skillhub.search.local-file-index.directory"))
                .isEqualTo("/var/lib/skillhub/search-index");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
    }

    private ConfigurableEnvironment loadEnvironment(List<String> resourceNames,
                                                    Map<String, Object> envVars) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", envVars));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : resourceNames) {
            for (var propertySource : loader.load(resourceName, new ClassPathResource(resourceName))) {
                environment.getPropertySources().addLast(propertySource);
            }
        }
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    private void applyRuntimeStateDefaults(ConfigurableEnvironment environment) {
        environment.getPropertySources().addFirst(
                new MapPropertySource(
                        RuntimeStatePropertyDefaults.PROPERTY_SOURCE_NAME,
                        RuntimeStatePropertyDefaults.resolveOverrides(environment)
                )
        );
        ConfigurationPropertySources.attach(environment);
    }
}
