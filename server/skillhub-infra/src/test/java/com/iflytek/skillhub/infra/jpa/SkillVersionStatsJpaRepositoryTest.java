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

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:skillhub-infra-stats;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ContextConfiguration(classes = SkillVersionStatsJpaRepositoryTest.TestJpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SkillVersionStatsJpaRepositoryTest {

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
