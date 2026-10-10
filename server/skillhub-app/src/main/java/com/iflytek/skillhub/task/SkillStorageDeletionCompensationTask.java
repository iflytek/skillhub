package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.skill.service.SkillStorageDeletionCompensationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SkillStorageDeletionCompensationTask {

    private static final Logger logger = LoggerFactory.getLogger(SkillStorageDeletionCompensationTask.class);

    private final SkillStorageDeletionCompensationService compensationService;

    public SkillStorageDeletionCompensationTask(SkillStorageDeletionCompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryPendingCleanup() {
        int retried = compensationService.retryPendingCleanup();
        if (retried > 0) {
            logger.info("Retried {} pending hard-delete storage cleanup records", retried);
        }
    }
}
