package com.iflytek.skillhub.domain.security;

import com.iflytek.skillhub.domain.event.ReviewSubmittedEvent;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public ScanCompletedEventListener(SkillVersionRepository skillVersionRepository,
                                      SkillRepository skillRepository,
                                      ReviewTaskRepository reviewTaskRepository,
                                      ApplicationEventPublisher eventPublisher) {
        this.skillVersionRepository = skillVersionRepository;
        this.skillRepository = skillRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScanCompleted(ScanCompletedEvent event) {
        try {
            skillVersionRepository.findById(event.versionId())
                    .flatMap(version -> skillRepository.findById(version.getSkillId())
                            .map(skill -> new PendingReviewContext(
                                    skill.getId(),
                                    version.getId(),
                                    skill.getNamespaceId(),
                                    version.getCreatedBy()
                            )))
                    .ifPresent(context -> {
                        ReviewTask task = reviewTaskRepository.save(new ReviewTask(
                                context.versionId(),
                                context.namespaceId(),
                                context.submitterId()
                        ));
                        eventPublisher.publishEvent(new ReviewSubmittedEvent(
                                task.getId(),
                                context.skillId(),
                                context.versionId(),
                                context.submitterId(),
                                context.namespaceId()
                        ));
                    });
        } catch (Exception e) {
            log.error("Failed to create review task after scan completed, versionId={}", event.versionId(), e);
        }
    }

    private record PendingReviewContext(Long skillId, Long versionId, Long namespaceId, String submitterId) {}
}
