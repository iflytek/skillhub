package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:sql/migration-mysql"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SkillVersionJsonPersistenceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("skillhub_skill_version_json")
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
    private EntityManager entityManager;

    @Autowired
    private SkillVersionJpaRepository skillVersionRepository;

    @Test
    void persistsSkillVersionJsonFieldsAsPlainText() {
        UserAccount owner = new UserAccount("skill-owner", "Skill Owner", "owner@example.com", null);
        entityManager.persist(owner);

        Namespace namespace = new Namespace("team-skill", "Team Skill", owner.getId());
        entityManager.persist(namespace);

        Skill skill = new Skill(namespace.getId(), "demo-skill", owner.getId(), SkillVisibility.PUBLIC);
        entityManager.persist(skill);

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", owner.getId());
        version.setParsedMetadataJson("{\"name\":\"demo-skill\",\"keywords\":[\"mysql\",\"json\"]}");
        version.setManifestJson("{\"entry\":\"index.js\",\"runtime\":\"node\"}");

        SkillVersion saved = skillVersionRepository.saveAndFlush(version);
        entityManager.clear();

        SkillVersion reloaded = entityManager.find(SkillVersion.class, saved.getId());
        assertThat(reloaded.getParsedMetadataJson())
                .isEqualTo("{\"name\":\"demo-skill\",\"keywords\":[\"mysql\",\"json\"]}");
        assertThat(reloaded.getManifestJson())
                .isEqualTo("{\"entry\":\"index.js\",\"runtime\":\"node\"}");
    }
}
