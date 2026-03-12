package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillStar;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface JpaSkillStarRepository extends JpaRepository<SkillStar, Long>, SkillStarRepository {
    Optional<SkillStar> findBySkillIdAndUserId(Long skillId, String userId);
    Page<SkillStar> findByUserId(String userId, Pageable pageable);
    long countBySkillId(Long skillId);
}
