package com.iflytek.skillhub.domain.security;

import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ScanCompletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ScanCompletedEventListener.class);

    private final SkillVersionRepository skillVersionRepository;
    private final SkillRepository skillRepository;
    private final ReviewTaskRepository reviewTaskRepository;

    public ScanCompletedEventListener(SkillVersionRepository skillVersionRepository,
                                       SkillRepository skillRepository,
                                       ReviewTaskRepository reviewTaskRepository) {
        this.skillVersionRepository = skillVersionRepository;
        this.skillRepository = skillRepository;
        this.reviewTaskRepository = reviewTaskRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScanCompleted(ScanCompletedEvent event) {
        try {
            skillVersionRepository.findById(event.versionId())
                    .flatMap(version -> skillRepository.findById(version.getSkillId())
                            .map(skill -> new ReviewTask(
                                    event.versionId(), skill.getNamespaceId(), version.getCreatedBy())))
                    .ifPresentOrElse(
                            reviewTask -> {
                                reviewTaskRepository.save(reviewTask);
                                log.info("Created ReviewTask for versionId={}, verdict={}, findings={}",
                                        event.versionId(), event.verdict(), event.findingsCount());
                            },
                            () -> log.warn("SkillVersion or Skill not found for versionId={}", event.versionId())
                    );
        } catch (Exception e) {
            log.error("Failed to create ReviewTask after scan for versionId={}", event.versionId(), e);
        }
    }
}
