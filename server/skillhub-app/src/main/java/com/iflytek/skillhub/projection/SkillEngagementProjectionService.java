package com.iflytek.skillhub.projection;

import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Maintains denormalized social counters on the skill read model.
 */
@Service
public class SkillEngagementProjectionService {

    private final JdbcTemplate jdbcTemplate;
    private final SkillStarRepository starRepository;
    private final SkillRatingRepository ratingRepository;

    public SkillEngagementProjectionService(JdbcTemplate jdbcTemplate,
                                            SkillStarRepository starRepository,
                                            SkillRatingRepository ratingRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.starRepository = starRepository;
        this.ratingRepository = ratingRepository;
    }

    public void refreshStarCount(Long skillId) {
        long count = starRepository.countBySkillId(skillId);
        jdbcTemplate.update("UPDATE skill SET star_count = ? WHERE id = ?", (int) count, skillId);
    }

    public void refreshRatingStats(Long skillId) {
        double avg = ratingRepository.averageScoreBySkillId(skillId);
        int count = ratingRepository.countBySkillId(skillId);
        jdbcTemplate.update(
                "UPDATE skill SET rating_avg = ?, rating_count = ? WHERE id = ?",
                avg,
                count,
                skillId
        );
    }
}
