package com.iflytek.skillhub.config;

import com.iflytek.skillhub.bootstrap.LocalFileIndexStartupSynchronizer;
import com.iflytek.skillhub.search.SearchRebuildService;
import java.io.IOException;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.RandomValuePropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class QaProfileBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(SearchRuntimeProperties.class, LocalFileIndexStartupSynchronizer.class)
            .withPropertyValues(
                    "skillhub.search.provider=local-file-index",
                    "skillhub.search.local-file-index.directory=/tmp/qa-search-index"
            );

    @Test
    void qaProfileProvidesH2DatasourceForAutomatedTests() throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        RandomValuePropertySource.addToEnvironment(environment);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String resourceName : java.util.List.of("application-qa.yml", "application.yml")) {
            for (var propertySource : loader.load(resourceName, new ClassPathResource(resourceName))) {
                environment.getPropertySources().addLast(propertySource);
            }
        }
        ConfigurationPropertySources.attach(environment);

        assertThat(environment.getProperty("spring.datasource.url", ""))
                .startsWith("jdbc:h2:mem:testdb-")
                .contains("MODE=PostgreSQL");
        assertThat(environment.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
        assertThat(environment.getProperty("spring.jpa.database-platform"))
                .isEqualTo("org.hibernate.dialect.H2Dialect");
        assertThat(environment.getProperty("skillhub.runtime.state.provider")).isEqualTo("memory");
        assertThat(environment.getProperty("skillhub.search.provider")).isEqualTo("local-file-index");
        assertThat(environment.getProperty("skillhub.search.startup-sync-enabled")).isEqualTo("false");
    }

    @Test
    void qaProfileDisablesLocalFileIndexStartupSynchronizerBean() {
        contextRunner
                .withPropertyValues("spring.profiles.active=qa", "skillhub.search.startup-sync-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LocalFileIndexStartupSynchronizer.class));
    }

    @Test
    void startupSynchronizerBeanIsPresentWhenQaStartupSyncRemainsEnabled() {
        contextRunner
                .withBean(SearchRebuildService.class, (Supplier<SearchRebuildService>) () -> new SearchRebuildService() {
                    @Override
                    public void rebuildAll() {
                    }

                    @Override
                    public void rebuildByNamespace(Long namespaceId) {
                    }

                    @Override
                    public void rebuildBySkill(Long skillId) {
                    }
                })
                .withPropertyValues("spring.profiles.active=qa", "skillhub.search.startup-sync-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LocalFileIndexStartupSynchronizer.class));
    }
}
