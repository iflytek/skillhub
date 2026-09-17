package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Records recoverable or terminal Bundle failures outside a rolled-back member transaction. */
@Service
public class SkillSuiteBundleOperationStateService {

    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final Clock clock;

    public SkillSuiteBundleOperationStateService(
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleMemberResultRepository memberRepository,
            Clock clock
    ) {
        this.operationRepository = operationRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRepreviewRequired(String operationId, String code) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElse(null);
        if (operation == null || terminal(operation.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        operation.markRepreviewRequired(code, now);
        List<SkillSuiteBundleMemberResult> members =
                memberRepository.findByOperationIdOrderByPositionForUpdate(operationId);
        members.forEach(member -> {
            if (member.getStatus()
                    != com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus.COMPLETED) {
                member.markRepreviewRequired(code, now);
            }
        });
        memberRepository.saveAll(members);
        operationRepository.save(operation);
        operationRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markBlockedRetryable(String operationId, String code, String detail) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElse(null);
        if (operation == null || terminal(operation.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        operation.markBlockedRetryable(code, detail, now);
        List<SkillSuiteBundleMemberResult> members =
                memberRepository.findByOperationIdOrderByPositionForUpdate(operationId);
        members.stream()
                .filter(member -> member.getStatus()
                        == com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus.PLANNED
                        || member.getStatus()
                        == com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultStatus.RUNNING)
                .findFirst()
                .ifPresent(member -> member.markBlockedRetryable(code, now));
        memberRepository.saveAll(members);
        operationRepository.save(operation);
        operationRepository.flush();
    }

    private boolean terminal(SkillSuiteBundleOperationStatus status) {
        return status == SkillSuiteBundleOperationStatus.REPREVIEW_REQUIRED
                || status == SkillSuiteBundleOperationStatus.SUITE_DRAFT_CREATED
                || status == SkillSuiteBundleOperationStatus.CANCELLED;
    }
}
