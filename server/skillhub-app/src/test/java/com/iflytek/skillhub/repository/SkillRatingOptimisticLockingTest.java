package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.social.SkillRating;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
class SkillRatingOptimisticLockingTest {

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

            first.updateReview((short) 5, "First update");
            firstManager.getTransaction().commit();

            stale.updateReview((short) 2, "Stale update");
            assertThatThrownBy(staleManager.getTransaction()::commit)
                    .satisfies(error -> assertThat(hasCause(error, OptimisticLockException.class)).isTrue());
        } finally {
            rollbackIfActive(firstManager);
            rollbackIfActive(staleManager);
            firstManager.close();
            staleManager.close();
            deleteRating(ratingId);
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
