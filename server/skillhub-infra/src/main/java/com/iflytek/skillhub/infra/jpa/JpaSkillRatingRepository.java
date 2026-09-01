package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/**
 * JPA-backed repository for per-user skill ratings and their derived aggregates.
 */
@Repository
public interface JpaSkillRatingRepository extends JpaRepository<SkillRating, Long>, SkillRatingRepository {
    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, String userId);

    @Query("""
            SELECT r FROM SkillRating r
            WHERE r.skillId = :skillId
              AND r.reviewStatus = com.iflytek.skillhub.domain.social.SkillReviewStatus.VISIBLE
              AND r.reviewText IS NOT NULL
              AND TRIM(r.reviewText) <> ''
            ORDER BY r.updatedAt DESC, r.id DESC
            """)
    Page<SkillRating> findVisibleReviewsBySkillId(Long skillId, Pageable pageable);

    @Query("""
            SELECT r FROM SkillRating r
            WHERE r.skillId = :skillId
              AND r.reviewText IS NOT NULL
              AND TRIM(r.reviewText) <> ''
            ORDER BY r.updatedAt DESC, r.id DESC
            """)
    Page<SkillRating> findReviewsBySkillId(Long skillId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM SkillRating r WHERE r.skillId = :skillId")
    double averageScoreBySkillId(Long skillId);

    int countBySkillId(Long skillId);

    void deleteBySkillId(Long skillId);
}
