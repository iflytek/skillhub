package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersion;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaReviewProgressQueryRepository.class)
@Testcontainers
class JpaReviewProgressQueryRepositoryTest {

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

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JpaReviewProgressQueryRepository repository;

    @Test
    void groupsAttemptsFiltersLatestStatusAndKeepsTotalsOnEmptyPage() {
        persistUsers("owner", "author-1", "other-author");
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("team-review", "Review Team", "owner"));
        Skill alpha = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "alpha-skill", "author-1", SkillVisibility.PUBLIC));
        Skill beta = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "beta-skill", "author-1", SkillVisibility.PUBLIC));
        Skill gamma = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "gamma-skill", "author-1", SkillVisibility.PUBLIC));

        persistAttempt(
                alpha,
                namespace,
                "author-1",
                "1.0.0",
                ReviewTaskStatus.REJECTED,
                Instant.parse("2026-08-30T10:00:00Z"));
        persistAttempt(
                alpha,
                namespace,
                "author-1",
                "1.0.0",
                ReviewTaskStatus.PENDING,
                Instant.parse("2026-08-31T10:00:00Z"));
        persistAttempt(
                beta,
                namespace,
                "author-1",
                "2.0.0",
                ReviewTaskStatus.APPROVED,
                Instant.parse("2026-08-29T10:00:00Z"));
        persistAttempt(
                gamma,
                namespace,
                "author-1",
                "3.0.0",
                ReviewTaskStatus.REJECTED,
                Instant.parse("2026-08-28T10:00:00Z"));
        persistAttempt(
                beta,
                namespace,
                "other-author",
                "3.0.0",
                ReviewTaskStatus.REJECTED,
                Instant.parse("2026-08-31T11:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        var firstPage = repository.findMyProgress("author-1", null, "", 0, 1);

        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.items()).singleElement().satisfies(item -> {
            assertThat(item.skillSlug()).isEqualTo("alpha-skill");
            assertThat(item.latestStatus()).isEqualTo("PENDING");
            assertThat(item.attemptCount()).isEqualTo(2);
        });
        assertThat(firstPage.statusCounts().pending()).isEqualTo(1);
        assertThat(firstPage.statusCounts().approved()).isEqualTo(1);
        assertThat(firstPage.statusCounts().rejected()).isEqualTo(1);

        var emptyPage = repository.findMyProgress("author-1", null, "", 8, 1);
        assertThat(emptyPage.items()).isEmpty();
        assertThat(emptyPage.total()).isEqualTo(3);

        var maximumPage = repository.findMyProgress(
                "author-1", null, "", Integer.MAX_VALUE, 100);
        assertThat(maximumPage.items()).isEmpty();
        assertThat(maximumPage.total()).isEqualTo(3);

        var searchedAndFiltered = repository.findMyProgress(
                "author-1", ReviewTaskStatus.APPROVED, "BETA", 0, 20);
        assertThat(searchedAndFiltered.items()).singleElement()
                .satisfies(item -> assertThat(item.skillSlug()).isEqualTo("beta-skill"));
        assertThat(searchedAndFiltered.total()).isEqualTo(1);
        assertThat(searchedAndFiltered.statusCounts().approved()).isEqualTo(1);

        var searchMiss = repository.findMyProgress("author-1", null, "missing", 0, 20);
        assertThat(searchMiss.items()).isEmpty();
        assertThat(searchMiss.total()).isZero();
        assertThat(searchMiss.statusCounts().pending()).isZero();
        assertThat(searchMiss.statusCounts().approved()).isZero();
        assertThat(searchMiss.statusCounts().rejected()).isZero();
    }

    @Test
    void includesSuiteAttemptsWithoutRequiringLegacySkillColumns() {
        persistUsers("owner", "author-1");
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("team-suite-review", "Suite Review Team", "owner"));
        SkillSuite suite = entityManager.persistFlushFind(
                new SkillSuite(namespace.getId(), "starter-pack", "Starter Pack", "author-1"));
        SkillSuiteVersion suiteVersion = entityManager.persistFlushFind(
                new SkillSuiteVersion(suite.getId(), "1.0.0", SkillVisibility.PUBLIC, "author-1"));
        ReviewTask task = ReviewTask.forSuiteVersion(
                suiteVersion.getId(), suite.getId(), namespace.getId(), suiteVersion.getVersion(), "author-1");
        entityManager.persist(task);
        entityManager.flush();
        entityManager.clear();

        var progress = repository.findMyProgress("author-1", null, "STARTER", 0, 20);

        assertThat(progress.items()).singleElement().satisfies(item -> {
            assertThat(item.skillId()).isNull();
            assertThat(item.skillSlug()).isNull();
            assertThat(item.subjectType()).isEqualTo("SUITE_VERSION");
            assertThat(item.subjectId()).isEqualTo(suite.getId());
            assertThat(item.subjectVersionId()).isEqualTo(suiteVersion.getId());
            assertThat(item.subjectSlug()).isEqualTo("starter-pack");
        });
        assertThat(progress.statusCounts().pending()).isEqualTo(1);
    }

    @Test
    void legacySkillReviewInsertPopulatesTypedSubjectDuringRollingUpgrade() {
        entityManager.persist(new UserAccount(
                "legacy-review-author", "Legacy Review Author", null, null));
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("legacy-review", "Legacy Review", "legacy-review-author"));
        Skill skill = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "legacy-skill", "legacy-review-author", SkillVisibility.PUBLIC));
        SkillVersion skillVersion = entityManager.persistFlushFind(
                new SkillVersion(skill.getId(), "1.0.0", "legacy-review-author"));

        int inserted = entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO review_task (
                    skill_version_id, skill_id, skill_version, namespace_id,
                    status, version, submitted_by, submitted_at
                ) VALUES (
                    :skillVersionId, :skillId, :skillVersion, :namespaceId,
                    'PENDING', 1, :submittedBy, CURRENT_TIMESTAMP
                )
                """)
                .setParameter("skillVersionId", skillVersion.getId())
                .setParameter("skillId", skill.getId())
                .setParameter("skillVersion", skillVersion.getVersion())
                .setParameter("namespaceId", namespace.getId())
                .setParameter("submittedBy", "legacy-review-author")
                .executeUpdate();

        Object[] typedSubject = (Object[]) entityManager.getEntityManager().createNativeQuery("""
                SELECT subject_type, subject_id, subject_version_id, subject_version
                FROM review_task
                WHERE skill_version_id = :skillVersionId
                """)
                .setParameter("skillVersionId", skillVersion.getId())
                .getSingleResult();

        assertThat(inserted).isEqualTo(1);
        assertThat(typedSubject).containsExactly(
                "SKILL_VERSION", skill.getId(), skillVersion.getId(), "1.0.0");
    }

    private void persistUsers(String... userIds) {
        for (String userId : userIds) {
            entityManager.persist(new UserAccount(userId, userId, null, null));
        }
        entityManager.flush();
    }

    private void persistAttempt(
            Skill skill,
            Namespace namespace,
            String author,
            String version,
            ReviewTaskStatus status,
            Instant submittedAt) {
        ReviewTask task = new ReviewTask(
                null, skill.getId(), namespace.getId(), version, author);
        task.setStatus(status);
        setField(task, "submittedAt", submittedAt);
        entityManager.persist(task);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
