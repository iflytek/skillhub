package com.iflytek.skillhub.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalMysqlProfileBindingTest {

    @Test
    void localMysqlProfile_usesMysqlDatasourceAndRedisBackedRuntimeDefaults() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-local-mysql.yml", "application.yml"),
                Map.of()
        );
        applyRuntimeStateDefaults(environment);

        assertEquals(
                "jdbc:mysql://localhost:3306/skillhub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8&useUnicode=true",
                environment.getProperty("spring.datasource.url")
        );
        assertEquals("com.mysql.cj.jdbc.Driver", environment.getProperty("spring.datasource.driver-class-name"));
        assertEquals("org.hibernate.dialect.MySQLDialect", environment.getProperty("spring.jpa.database-platform"));
        assertEquals("none", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("true", environment.getProperty("spring.flyway.enabled"));
        assertEquals("classpath:sql/migration-mysql", environment.getProperty("spring.flyway.locations"));
        assertEquals("local", environment.getProperty("skillhub.storage.provider"));
        assertEquals("mysql", environment.getProperty("skillhub.search.engine"));
        assertEquals("mysql-like", environment.getProperty("skillhub.search.provider"));
        assertEquals("redis", environment.getProperty("skillhub.runtime.state.provider"));
        assertEquals(
                System.getProperty("user.home") + "/.skillhub/local-mysql/storage",
                environment.getProperty("skillhub.storage.local.base-path")
        );
        assertEquals(
                System.getProperty("user.home") + "/.skillhub/local-mysql/search-index",
                environment.getProperty("skillhub.search.local-file-index.directory")
        );
        assertEquals("redis", environment.getProperty("skillhub.ratelimit.mode"));
        assertEquals("redis", environment.getProperty("skillhub.auth.failure-throttle.mode"));
        assertEquals("redis", environment.getProperty("skillhub.auth.uass.cache-mode"));
        assertEquals("false", environment.getProperty("skillhub.auth.uass.enabled"));
        assertEquals("redis", environment.getProperty("spring.session.store-type"));
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("false", environment.getProperty("server.servlet.session.cookie.secure"));
        assertThat(environment.getProperty("spring.autoconfigure.exclude", String[].class)).isEmpty();
    }

    @Test
    void localMysqlProfile_doesNotHardcodeMemoryOnlySessionOverrides() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-local-mysql.yml", "application.yml"),
                Map.of()
        );

        assertEquals("redis", environment.getProperty("spring.session.store-type"));
        assertThat(environment.getProperty("spring.autoconfigure.exclude", String[].class)).isNull();
    }

    @Test
    void localMysqlProfile_canFallbackToMemoryBackedRuntimeState() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-local-mysql.yml", "application.yml"),
                Map.of("SKILLHUB_RUNTIME_STATE_PROVIDER", "memory")
        );
        applyRuntimeStateDefaults(environment);

        assertEquals("memory", environment.getProperty("skillhub.runtime.state.provider"));
        assertEquals("memory", environment.getProperty("skillhub.ratelimit.mode"));
        assertEquals("memory", environment.getProperty("skillhub.auth.failure-throttle.mode"));
        assertEquals("local", environment.getProperty("skillhub.auth.uass.cache-mode"));
        assertEquals("none", environment.getProperty("spring.session.store-type"));
        assertThat(environment.getProperty("spring.autoconfigure.exclude", String[].class))
                .containsExactlyElementsOf(RuntimeStatePropertyDefaults.memoryAutoConfigurationExcludes());
    }

    @Test
    void localMysqlProfile_canSwitchSearchProviderAndDirectoryForPhaseThree() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-local-mysql.yml", "application.yml"),
                Map.of(
                        "SKILLHUB_SEARCH_PROVIDER", "local-file-index",
                        "SKILLHUB_SEARCH_LOCAL_FILE_INDEX_DIRECTORY", "/tmp/skillhub-lucene"
                )
        );

        SearchRuntimeProperties properties = Binder.get(environment)
                .bind("skillhub.search", SearchRuntimeProperties.class)
                .orElseThrow(() -> new AssertionError("skillhub.search properties should bind"));

        assertEquals("mysql", environment.getProperty("skillhub.search.engine"));
        assertEquals("local-file-index", properties.getProvider());
        assertEquals(Path.of("/tmp/skillhub-lucene"), properties.getLocalFileIndex().getDirectory());
    }

    private ConfigurableEnvironment loadEnvironment(List<String> resourceNames,
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
