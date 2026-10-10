package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.SkillDraftRepository;
import com.iflytek.skillhub.domain.authoring.validation.FindingDraft;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEvent;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventType;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFindingRepository;
import com.iflytek.skillhub.domain.authoring.validation.ValidationEventRepository;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunRepository;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for validation run lifecycle: starting runs bound to a frozen draft
 * revision, appending ordered events, recording findings, and settling runs into
 * terminal states. Events and findings commit in their own transactions so partial
 * progress is always visible to SSE subscribers and polling clients.
 */
@Service
public class ValidationRunService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final List<ValidationRunStatus> ACTIVE_STATUSES = List.of(
            ValidationRunStatus.QUEUED, ValidationRunStatus.PREPARING, ValidationRunStatus.RUNNING);

    private final ValidationRunRepository runRepository;
    private final ValidationEventRepository eventRepository;
    private final ValidationFindingRepository findingRepository;
    private final SkillDraftRepository draftRepository;
    private final Clock clock;

    public ValidationRunService(ValidationRunRepository runRepository,
                                ValidationEventRepository eventRepository,
                                ValidationFindingRepository findingRepository,
                                SkillDraftRepository draftRepository,
                                Clock clock) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.findingRepository = findingRepository;
        this.draftRepository = draftRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Creates a QUEUED run bound to the draft's current revision. Only one active run
     * per draft is allowed.
     */
    @Transactional
    public ValidationRun startRun(Long draftId, String userId, Set<String> platformRoles) {
        SkillDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.draft.notFound", draftId));
        assertOwner(draft, userId, platformRoles);
        if (runRepository.findByDraftIdOrderByCreatedAtDesc(draftId).stream()
                .anyMatch(ValidationRun::isActive)) {
            throw new DomainConflictException("error.authoring.run.activeExists", draftId);
        }
        return runRepository.save(
                new ValidationRun(draftId, draft.getRevision(), userId));
    }

    /**
     * Transitions a QUEUED run through PREPARING to RUNNING under a row lock, so
     * cancellation and execution cannot interleave. Returns the refreshed run.
     */
    @Transactional
    public ValidationRun claimForExecution(Long runId) {
        ValidationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.run.notFound", runId));
        if (run.getStatus() == ValidationRunStatus.CANCELLED) {
            throw new DomainConflictException("error.authoring.run.cancelled", runId);
        }
        if (run.getStatus() != ValidationRunStatus.QUEUED) {
            throw new DomainConflictException("error.authoring.run.notQueued", runId);
        }
        if (run.isCancelRequested()) {
            run.finish(ValidationRunStatus.CANCELLED, 0, 0, null, clock.instant());
            return runRepository.save(run);
        }
        run.markPreparing();
        run.markRunning(clock.instant());
        return runRepository.save(run);
    }

    /**
     * Idempotent cancel request. Sets the cooperative flag and settles QUEUED runs
     * immediately; RUNNING/PREPARING runs are finished by their executor thread when
     * it observes the flag (or by the maintenance sweep).
     */
    @Transactional
    public ValidationRun requestCancel(Long runId, String userId, Set<String> platformRoles) {
        ValidationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.run.notFound", runId));
        SkillDraft draft = draftRepository.findById(run.getDraftId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.authoring.draft.notFound", run.getDraftId()));
        assertOwner(draft, userId, platformRoles);

        if (!run.isActive()) {
            return run; // already terminal: cancel is idempotent
        }
        run.requestCancel();
        if (run.getStatus() == ValidationRunStatus.QUEUED) {
            run.finish(ValidationRunStatus.CANCELLED, 0, 0,
                    Map.of("reason", "cancelled before start"), clock.instant());
        }
        return runRepository.save(run);
    }

    /**
     * Settles a run into a terminal status if it is still active; returns empty when the
     * run was already terminal (idempotent settle — the first terminal state wins). When
     * the run succeeds with zero errors and the draft has not moved on, the draft is
     * marked validated for this revision.
     */
    @Transactional
    public Optional<ValidationRun> settleIfActive(Long runId, ValidationRunStatus terminalStatus,
                                                  int errorCount, int warningCount,
                                                  Map<String, Object> summary) {
        ValidationRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.run.notFound", runId));
        if (run.getStatus().isTerminal()) {
            return Optional.empty();
        }
        run.finish(terminalStatus, errorCount, warningCount, summary, clock.instant());
        ValidationRun settled = runRepository.save(run);

        if (terminalStatus == ValidationRunStatus.SUCCEEDED && errorCount == 0) {
            draftRepository.findById(settled.getDraftId()).ifPresent(draft -> {
                if (draft.getRevision().equals(settled.getDraftRevision())) {
                    draft.markValidated(settled.getDraftRevision(), settled.getId());
                    draftRepository.save(draft);
                }
            });
        }
        return Optional.of(settled);
    }

    // ---------------------------------------------------------------- events & findings

    /** Appends one event with the next sequence number; commits independently. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ValidationEvent appendEvent(Long runId, ValidationEventType type, String phase,
                                       Map<String, Object> payload) {
        int nextSeq = eventRepository.findMaxSeqByRunId(runId).map(seq -> seq + 1).orElse(1);
        return eventRepository.save(
                new ValidationEvent(runId, nextSeq, type, phase, payload));
    }

    /** Records one finding and returns it; commits independently. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ValidationFinding recordFinding(Long runId, FindingDraft draft) {
        return findingRepository.save(new ValidationFinding(
                runId, draft.layer(), draft.ruleCode(), draft.severity(),
                draft.filePath(), draft.location(), draft.message(), draft.suggestion()));
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public ValidationRun getOwnedRun(Long runId, String userId, Set<String> platformRoles) {
        ValidationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.run.notFound", runId));
        SkillDraft draft = draftRepository.findById(run.getDraftId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.authoring.draft.notFound", run.getDraftId()));
        assertOwner(draft, userId, platformRoles);
        return run;
    }

    /** Trusted accessor for internal executors (validation pipeline, maintenance sweep). */
    @Transactional(readOnly = true)
    public ValidationRun getRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.run.notFound", runId));
    }

    @Transactional(readOnly = true)
    public List<ValidationRun> listRuns(Long draftId) {
        return runRepository.findByDraftIdOrderByCreatedAtDesc(draftId);
    }

    @Transactional(readOnly = true)
    public List<ValidationEvent> listEvents(Long runId, Integer afterSeq) {
        if (afterSeq == null) {
            return eventRepository.findByRunIdOrderBySeqAsc(runId);
        }
        return eventRepository.findByRunIdAndSeqGreaterThanOrderBySeqAsc(runId, afterSeq);
    }

    @Transactional(readOnly = true)
    public List<ValidationFinding> listFindings(Long runId) {
        List<ValidationFinding> findings = findingRepository.findByRunId(runId);
        findings.sort(Comparator
                .comparing(ValidationFinding::getSeverity)
                .thenComparing(ValidationFinding::getId));
        return findings;
    }

    @Transactional(readOnly = true)
    public ValidationFinding getFinding(Long findingId) {
        return findingRepository.findById(findingId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.authoring.finding.notFound", findingId));
    }

    @Transactional(readOnly = true)
    public List<ValidationRun> findActiveRuns() {
        return runRepository.findByStatusIn(ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<ValidationRun> findActiveRunsCreatedBefore(Instant cutoff) {
        return runRepository.findByStatusInAndCreatedAtBefore(ACTIVE_STATUSES, cutoff);
    }

    /**
     * The submission gate: the latest run bound to the draft's current revision must
     * have succeeded with zero errors.
     */
    @Transactional(readOnly = true)
    public ValidationRun requirePassingRunForRevision(Long draftId, Integer revision) {
        ValidationRun run = runRepository
                .findFirstByDraftIdAndDraftRevisionOrderByCreatedAtDesc(draftId, revision)
                .orElseThrow(() -> new DomainConflictException(
                        "error.authoring.submit.notValidated", revision));
        if (run.getStatus() != ValidationRunStatus.SUCCEEDED || run.getErrorCount() > 0) {
            throw new DomainConflictException("error.authoring.submit.notValidated", revision);
        }
        return run;
    }

    private void assertOwner(SkillDraft draft, String userId, Set<String> platformRoles) {
        if (draft.getOwnerId().equals(userId)
                || (platformRoles != null && platformRoles.contains(SUPER_ADMIN))) {
            return;
        }
        throw new DomainForbiddenException("error.authoring.draft.forbidden", draft.getId());
    }
}
