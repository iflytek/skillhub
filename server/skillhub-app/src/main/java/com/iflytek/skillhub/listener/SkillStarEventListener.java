package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the stored star count in sync with the star/unstar event stream.
 */
@Component
public class SkillStarEventListener {
    private final JdbcTemplate jdbcTemplate;
    private final SkillStarRepository starRepository;

    public SkillStarEventListener(JdbcTemplate jdbcTemplate, SkillStarRepository starRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.starRepository = starRepository;
    }

    @Async
    @TransactionalEventListener
    public void onStarred(SkillStarredEvent event) {
        updateStarCount(event.skillId());
    }

    @Async
    @TransactionalEventListener
    public void onUnstarred(SkillUnstarredEvent event) {
        updateStarCount(event.skillId());
    }

    private void updateStarCount(Long skillId) {
        long count = starRepository.countBySkillId(skillId);
        jdbcTemplate.update("UPDATE skill SET star_count = ? WHERE id = ?", (int) count, skillId);
    }
}
