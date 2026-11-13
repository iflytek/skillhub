package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.event.ReviewSubmittedEvent;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Returns review tasks that lost team-only automatic publication eligibility to the normal queue.
 *
 * <p>Cancellation is irreversible and is written with conditional updates rather than by mutating a
 * managed task, so it neither bumps the review optimistic version nor races a human decision. The
 * existing {@link ReviewSubmittedEvent} is emitted only by the transaction that first flips the
 * notification marker, which keeps fallback notification to exactly once per task.
 */
@Service
public class ReviewAutoPublicationHandoff {

    private final ReviewTaskRepository reviewTaskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewAutoPublicationHandoff(ReviewTaskRepository reviewTaskRepository,
                                        ApplicationEventPublisher eventPublisher) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Cancels every pending eligible task submitted by a member in one namespace. Callers must hold
     * the namespace row lock so this serializes with automatic approval.
     */
    public int handOffMember(Long namespaceId, String userId) {
        List<ReviewTask> pending = reviewTaskRepository.findByNamespaceIdAndSubmittedByAndStatus(
                namespaceId, userId, ReviewTaskStatus.PENDING);
        int handedOff = 0;
        for (ReviewTask task : pending) {
            if (task.getAutoPublicationGrantId() != null && !task.isAutoPublicationCancelled()) {
                handOff(task);
                handedOff++;
            }
        }
        return handedOff;
    }

    /** Cancels one task's automatic eligibility and announces it to normal reviewers once. */
    public void handOff(ReviewTask task) {
        reviewTaskRepository.cancelPendingAutoPublication(task.getId());
        if (reviewTaskRepository.markPendingManualReviewNotified(task.getId()) == 1) {
            eventPublisher.publishEvent(new ReviewSubmittedEvent(
                    task.getId(),
                    task.getSkillId(),
                    task.getSkillVersionId(),
                    task.getSubmittedBy(),
                    task.getNamespaceId()));
        }
    }
}
