package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.db.MysqlContainerBackedDataJpaTest;
import com.iflytek.skillhub.db.MysqlMigrationDataJpaTest;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.SkillVersionJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

@MysqlMigrationDataJpaTest
@Testcontainers
class SkillVersionJsonPersistenceTest extends MysqlContainerBackedDataJpaTest {

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
