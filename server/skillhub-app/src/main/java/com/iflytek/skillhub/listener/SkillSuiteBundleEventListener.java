package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.event.ReviewRejectedEvent;
import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.event.SkillSuiteBundleAdvanceRequestedEvent;
import com.iflytek.skillhub.domain.event.SkillVersionYankedEvent;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.service.bundle.SkillSuiteBundleCoordinator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Wakes durable Bundle coordination after confirmation and member lifecycle events. */
@Component
public class SkillSuiteBundleEventListener {

    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final SkillSuiteBundleCoordinator coordinator;

    public SkillSuiteBundleEventListener(
            SkillSuiteBundleMemberResultRepository memberRepository,
            SkillSuiteBundleCoordinator coordinator
    ) {
        this.memberRepository = memberRepository;
        this.coordinator = coordinator;
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAdvanceRequested(SkillSuiteBundleAdvanceRequestedEvent event) {
        coordinator.advance(event.operationId());
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSkillPublished(SkillPublishedEvent event) {
        advanceBoundOperations(event.versionId());
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewRejected(ReviewRejectedEvent event) {
        advanceBoundOperations(event.versionId());
    }

    @Async("skillhubEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSkillVersionYanked(SkillVersionYankedEvent event) {
        advanceBoundOperations(event.versionId());
    }

    private void advanceBoundOperations(Long skillVersionId) {
        memberRepository.findBySkillVersionId(skillVersionId).stream()
                .map(member -> member.getOperationId())
                .distinct()
                .forEach(coordinator::advance);
    }
}
