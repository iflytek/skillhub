package com.iflytek.skillhub.infra.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.skill.SkillVersionStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect"
})
@ContextConfiguration(classes = SkillVersionStatsJpaRepositoryTest.TestJpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SkillVersionStatsJpaRepositoryTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillhub_infra_stats")
            .withUsername("skillhub")
            .withPassword("skillhub");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private SkillVersionStatsJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldRunCustomDownloadCounterQueries() {
        repository.insertInitialDownloadCount(100L, 200L);
        entityManager.flush();
        entityManager.clear();

        SkillVersionStats inserted = entityManager.find(SkillVersionStats.class, 100L);
        assertThat(inserted).isNotNull();
        assertThat(inserted.getSkillId()).isEqualTo(200L);
        assertThat(inserted.getDownloadCount()).isEqualTo(1L);

        repository.incrementExistingDownloadCount(100L);
        entityManager.flush();
        entityManager.clear();

        SkillVersionStats incremented = entityManager.find(SkillVersionStats.class, 100L);
        assertThat(incremented.getDownloadCount()).isEqualTo(2L);

        repository.deleteBySkillId(200L);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(SkillVersionStats.class, 100L)).isNull();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = SkillVersionStats.class)
    @EnableJpaRepositories(
            basePackageClasses = SkillVersionStatsJpaRepository.class,
            includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SkillVersionStatsJpaRepository.class)
    )
    static class TestJpaConfig {
    }
}
