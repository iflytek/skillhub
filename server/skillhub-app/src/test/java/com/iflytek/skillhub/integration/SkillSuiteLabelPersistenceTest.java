package com.iflytek.skillhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelRepository;
import com.iflytek.skillhub.domain.label.SkillSuiteLabel;
import com.iflytek.skillhub.domain.label.SkillSuiteLabelRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillTag;
import com.iflytek.skillhub.domain.skill.SkillTagRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.user.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SkillSuiteLabelPersistenceTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private TestEntityManager entityManager;
    @Autowired private SkillSuiteLabelRepository suiteLabelRepository;
    @Autowired private SkillLabelRepository skillLabelRepository;
    @Autowired private SkillTagRepository skillTagRepository;

    @Test
    void deletingSuiteRemovesOnlySuiteLabelsAndKeepsMemberSkillMetadata() {
        Fixture fixture = persistFixture("suite-delete");

        entityManager.remove(entityManager.find(SkillSuite.class, fixture.suite().getId()));
        entityManager.flush();
        entityManager.clear();

        assertThat(suiteLabelRepository.findBySuiteId(fixture.suite().getId())).isEmpty();
        assertThat(skillLabelRepository.findBySkillId(fixture.skill().getId()))
                .extracting(SkillLabel::getLabelId)
                .containsExactly(fixture.label().getId());
        assertThat(skillTagRepository.findBySkillId(fixture.skill().getId()))
                .extracting(SkillTag::getTagName)
                .containsExactly("member-tag");
        assertThat(entityManager.find(Skill.class, fixture.skill().getId())).isNotNull();
        assertThat(entityManager.find(LabelDefinition.class, fixture.label().getId())).isNotNull();
    }

    @Test
    void deletingSuiteLabelAssociationDoesNotPropagateToMemberSkill() {
        Fixture fixture = persistFixture("association-delete");
        SkillSuiteLabel association = suiteLabelRepository
                .findBySuiteIdAndLabelId(fixture.suite().getId(), fixture.label().getId())
                .orElseThrow();

        suiteLabelRepository.delete(association);
        entityManager.flush();
        entityManager.clear();

        assertThat(suiteLabelRepository.findBySuiteId(fixture.suite().getId())).isEmpty();
        assertThat(skillLabelRepository.findBySkillId(fixture.skill().getId())).hasSize(1);
        assertThat(skillTagRepository.findBySkillId(fixture.skill().getId())).hasSize(1);
    }

    private Fixture persistFixture(String suffix) {
        String userId = "suite-label-" + suffix;
        entityManager.persist(new UserAccount(userId, "Suite Label Owner", null, null));
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("suite-label-" + suffix, "Suite Label Namespace", userId));
        Skill skill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "member", userId, SkillVisibility.PUBLIC));
        SkillVersion version = entityManager.persistFlushFind(
                new SkillVersion(skill.getId(), "1.0.0", userId));
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "suite", "Suite", userId));
        LabelDefinition label = entityManager.persistFlushFind(
                new LabelDefinition("label-" + suffix, LabelType.RECOMMENDED, true, 0, userId));

        entityManager.persist(new SkillLabel(skill.getId(), label.getId(), userId));
        entityManager.persist(new SkillTag(skill.getId(), "member-tag", version.getId(), userId));
        entityManager.persist(new SkillSuiteLabel(suite.getId(), label.getId(), userId));
        entityManager.flush();
        entityManager.clear();

        return new Fixture(skill, suite, label);
    }

    private record Fixture(Skill skill, SkillSuite suite, LabelDefinition label) {
    }
}
