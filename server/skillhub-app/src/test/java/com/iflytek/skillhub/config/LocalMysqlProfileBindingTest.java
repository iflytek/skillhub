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

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalMysqlProfileBindingTest {

    @Test
    void localMysqlProfile_usesMysqlDatasourceAndMemoryBackedRuntimeDefaults() throws IOException {
        ConfigurableEnvironment environment = loadEnvironment(
                List.of("application-local-mysql.yml", "application.yml"),
                Map.of()
        );

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
        assertEquals(
                System.getProperty("user.home") + "/.skillhub/local-mysql/storage",
                environment.getProperty("skillhub.storage.local.base-path")
        );
        assertEquals("memory", environment.getProperty("skillhub.ratelimit.mode"));
        assertEquals("memory", environment.getProperty("skillhub.auth.failure-throttle.mode"));
        assertEquals("local", environment.getProperty("skillhub.auth.uass.cache-mode"));
        assertEquals("lax", environment.getProperty("server.servlet.session.cookie.same-site"));
        assertEquals("false", environment.getProperty("server.servlet.session.cookie.secure"));
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
}
