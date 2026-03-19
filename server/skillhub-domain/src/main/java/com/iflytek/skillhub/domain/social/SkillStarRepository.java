package com.iflytek.skillhub.domain.social;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository contract for skill star relationships and starred-skill pagination.
 */
public interface SkillStarRepository {
    SkillStar save(SkillStar star);
    Optional<SkillStar> findBySkillIdAndUserId(Long skillId, String userId);
    void delete(SkillStar star);
    Page<SkillStar> findByUserId(String userId, Pageable pageable);
    long countBySkillId(Long skillId);
}
