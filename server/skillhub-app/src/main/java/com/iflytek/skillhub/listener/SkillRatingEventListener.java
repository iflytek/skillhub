package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Updates denormalized skill rating counters when rating events are emitted by
 * the social domain.
 */
@Component
public class SkillRatingEventListener {
    private final JdbcTemplate jdbcTemplate;
    private final SkillRatingRepository ratingRepository;

    public SkillRatingEventListener(JdbcTemplate jdbcTemplate, SkillRatingRepository ratingRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.ratingRepository = ratingRepository;
    }

    @Async
    @TransactionalEventListener
    public void onRated(SkillRatedEvent event) {
        double avg = ratingRepository.averageScoreBySkillId(event.skillId());
        int count = ratingRepository.countBySkillId(event.skillId());
        jdbcTemplate.update(
            "UPDATE skill SET rating_avg = ?, rating_count = ? WHERE id = ?",
            avg, count, event.skillId());
    }
}
