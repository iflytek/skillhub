package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.JpaSkillVersionStatsRepositoryAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaSkillVersionStatsRepositoryAdapter.class)
class SkillVersionStatsPersistenceCompatibilityTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SkillVersionStatsRepository skillVersionStatsRepository;

    @Test
    void incrementsDownloadCountWithoutDatabaseSpecificUpsertSql() {
        UserAccount owner = new UserAccount("skill-owner-mysql-stats", "Skill Owner", "owner-stats@example.com", null);
        entityManager.persist(owner);

        Namespace namespace = new Namespace("team-skill-mysql-stats", "Team Skill", owner.getId());
        entityManager.persist(namespace);

        Skill skill = new Skill(namespace.getId(), "demo-skill-mysql-stats", owner.getId(), SkillVisibility.PUBLIC);
        entityManager.persist(skill);

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", owner.getId());
        entityManager.persist(version);
        entityManager.flush();

        skillVersionStatsRepository.incrementDownloadCount(version.getId(), skill.getId());
        entityManager.flush();
        entityManager.clear();

        SkillVersionStats firstWrite = entityManager.find(SkillVersionStats.class, version.getId());
        assertThat(firstWrite).isNotNull();
        assertThat(firstWrite.getSkillId()).isEqualTo(skill.getId());
        assertThat(firstWrite.getDownloadCount()).isEqualTo(1L);
        assertThat(firstWrite.getUpdatedAt()).isNotNull();

        skillVersionStatsRepository.incrementDownloadCount(version.getId(), skill.getId());
        entityManager.flush();
        entityManager.clear();

        SkillVersionStats secondWrite = entityManager.find(SkillVersionStats.class, version.getId());
        assertThat(secondWrite.getDownloadCount()).isEqualTo(2L);
    }
}
