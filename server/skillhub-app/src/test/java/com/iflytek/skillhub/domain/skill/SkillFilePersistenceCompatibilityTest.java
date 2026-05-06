package com.iflytek.skillhub.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.infra.jpa.SkillFileJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("qa")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SkillFilePersistenceCompatibilityTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SkillFileJpaRepository skillFileRepository;

    @Test
    void persistsAndLoadsSkillFilesUnderPortableSchema() {
        UserAccount owner = new UserAccount("skill-owner-mysql-file", "Skill Owner", "owner-file@example.com", null);
        entityManager.persist(owner);

        Namespace namespace = new Namespace("team-skill-mysql-file", "Team Skill", owner.getId());
        entityManager.persist(namespace);

        Skill skill = new Skill(namespace.getId(), "demo-skill-mysql-file", owner.getId(), SkillVisibility.PUBLIC);
        entityManager.persist(skill);

        SkillVersion version = new SkillVersion(skill.getId(), "1.0.0", owner.getId());
        entityManager.persist(version);
        entityManager.flush();

        SkillFile skillFile = new SkillFile(
                version.getId(),
                "SKILL.md",
                128L,
                "text/markdown",
                "abc123",
                "skills/" + skill.getId() + "/" + version.getId() + "/SKILL.md"
        );

        SkillFile saved = skillFileRepository.saveAndFlush(skillFile);
        entityManager.clear();

        List<SkillFile> reloaded = skillFileRepository.findByVersionId(version.getId());
        SkillFile reloadedFile = reloaded.get(0);
        assertThat(reloaded).hasSize(1);
        assertThat(reloadedFile.getId()).isEqualTo(saved.getId());
        assertThat(reloadedFile.getFilePath()).isEqualTo("SKILL.md");
        assertThat(reloadedFile.getStorageKey()).contains("/SKILL.md");
        assertThat(reloadedFile.getCreatedAt()).isNotNull();
    }
}
