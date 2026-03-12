package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JpaSkillRatingRepository extends JpaRepository<SkillRating, Long>, SkillRatingRepository {
    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, String userId);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM SkillRating r WHERE r.skillId = :skillId")
    double averageScoreBySkillId(Long skillId);

    int countBySkillId(Long skillId);
}
