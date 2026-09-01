package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillReviewStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class SkillRatingOptimisticLockingTest {

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
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentReviewUpdatesRejectTheStaleWriter() {
        Long ratingId = persistRating();
        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager staleManager = entityManagerFactory.createEntityManager();
        try {
            firstManager.getTransaction().begin();
            staleManager.getTransaction().begin();
            SkillRating first = firstManager.find(SkillRating.class, ratingId);
            SkillRating stale = staleManager.find(SkillRating.class, ratingId);

            first.hideReview("moderator", "Policy violation");
            firstManager.getTransaction().commit();

            stale.updateReview((short) 2, "Stale update");
            assertThatThrownBy(staleManager.getTransaction()::commit)
                    .satisfies(error -> assertThat(hasCause(error, OptimisticLockException.class)).isTrue());

            EntityManager verifier = entityManagerFactory.createEntityManager();
            try {
                SkillRating saved = verifier.find(SkillRating.class, ratingId);
                assertThat(saved.getReviewStatus()).isEqualTo(SkillReviewStatus.HIDDEN);
                assertThat(saved.getModerationReason()).isEqualTo("Policy violation");
            } finally {
                verifier.close();
            }
        } finally {
            rollbackIfActive(firstManager);
            rollbackIfActive(staleManager);
            firstManager.close();
            staleManager.close();
            deleteRating(ratingId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicateFirstInsertIsRejectedByTheDatabaseUniqueConstraint() {
        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager secondManager = entityManagerFactory.createEntityManager();
        try {
            firstManager.getTransaction().begin();
            secondManager.getTransaction().begin();
            assertThat(countRatings(firstManager, 20L, "duplicate-author")).isZero();
            assertThat(countRatings(secondManager, 20L, "duplicate-author")).isZero();

            SkillRating first = new SkillRating(20L, "duplicate-author", (short) 4);
            first.updateReview((short) 4, "First insert");
            firstManager.persist(first);
            firstManager.getTransaction().commit();

            SkillRating duplicate = new SkillRating(20L, "duplicate-author", (short) 5);
            duplicate.updateReview((short) 5, "Duplicate insert");
            assertThatThrownBy(() -> {
                secondManager.persist(duplicate);
                secondManager.flush();
                secondManager.getTransaction().commit();
            }).satisfies(error -> assertThat(hasCause(error, ConstraintViolationException.class)).isTrue());

            EntityManager verifier = entityManagerFactory.createEntityManager();
            try {
                assertThat(countRatings(verifier, 20L, "duplicate-author")).isEqualTo(1L);
            } finally {
                verifier.close();
            }
        } finally {
            rollbackIfActive(firstManager);
            rollbackIfActive(secondManager);
            firstManager.close();
            secondManager.close();
            deleteRatings(20L, "duplicate-author");
        }
    }

    private Long persistRating() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            SkillRating rating = new SkillRating(10L, "author", (short) 4);
            rating.updateReview((short) 4, "Original review");
            entityManager.persist(rating);
            entityManager.getTransaction().commit();
            return rating.getId();
        } finally {
            rollbackIfActive(entityManager);
            entityManager.close();
        }
    }

    private void rollbackIfActive(EntityManager entityManager) {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }

    private void deleteRating(Long ratingId) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            SkillRating rating = entityManager.find(SkillRating.class, ratingId);
            if (rating != null) {
                entityManager.remove(rating);
            }
            entityManager.getTransaction().commit();
        } finally {
            rollbackIfActive(entityManager);
            entityManager.close();
        }
    }

    private long countRatings(EntityManager entityManager, Long skillId, String userId) {
        return entityManager.createQuery("""
                        SELECT COUNT(r) FROM SkillRating r
                        WHERE r.skillId = :skillId AND r.userId = :userId
                        """, Long.class)
                .setParameter("skillId", skillId)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    private void deleteRatings(Long skillId, String userId) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            entityManager.createQuery("""
                            DELETE FROM SkillRating r
                            WHERE r.skillId = :skillId AND r.userId = :userId
                            """)
                    .setParameter("skillId", skillId)
                    .setParameter("userId", userId)
                    .executeUpdate();
            entityManager.getTransaction().commit();
        } finally {
            rollbackIfActive(entityManager);
            entityManager.close();
        }
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> expectedType) {
        Throwable current = error;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
