package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.SkillDraftRepository;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import java.time.Clock;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submits a validated draft into the existing SkillHub publish pipeline. The gate is
 * strict: the draft's current revision must be bound to a validation run that
 * succeeded with zero errors, so content can never be published without the exact
 * bytes that were validated. Publishing reuses {@link SkillPublishService}, which
 * re-validates the package, triggers the security scan, and opens the review flow.
 */
@Service
public class DraftSubmitService {

    private static final Logger log = LoggerFactory.getLogger(DraftSubmitService.class);
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    public record SubmitOutcome(Long skillId, Long versionId, String slug, String version,
                                SkillPublishService.PublishResult publishResult) {}

    private final SkillDraftRepository draftRepository;
    private final SkillDraftService draftService;
    private final ValidationRunService runService;
    private final SkillPublishService publishService;
    private final Clock clock;

    public DraftSubmitService(SkillDraftRepository draftRepository,
                              SkillDraftService draftService,
                              ValidationRunService runService,
                              SkillPublishService publishService,
                              Clock clock) {
        this.draftRepository = draftRepository;
        this.draftService = draftService;
        this.runService = runService;
        this.publishService = publishService;
        this.clock = clock;
    }

    /**
     * Publishes the draft's current revision as a new skill version. Warnings that the
     * validation run already surfaced to the author are confirmed here; errors were a
     * hard gate and cannot reach this point.
     */
    @Transactional
    public SubmitOutcome submit(Long draftId, String userId, SkillVisibility visibility,
                                Set<String> platformRoles) {
        SkillDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.draft.notFound", draftId));
        assertOwner(draft, userId, platformRoles);

        if (runService.listRuns(draftId).stream().anyMatch(ValidationRun::isActive)) {
            throw new DomainConflictException("error.authoring.submit.runActive", draftId);
        }
        ValidationRun passingRun = runService.requirePassingRunForRevision(
                draftId, draft.getRevision());

        String namespaceSlug = draftService.resolveNamespaceSlug(draft);
        java.util.List<PackageEntry> entries = draftService.materializeEntries(draftId);

        SkillPublishService.PublishResult result = publishService.publishFromEntries(
                namespaceSlug, entries, userId, visibility, platformRoles, true);

        draft.markSubmitted(result.skillId(), result.version().getId(), clock.instant());
        draftRepository.save(draft);
        log.info("Draft {} revision {} submitted as skill {} version {} (validation run {})",
                draftId, draft.getRevision(), result.skillId(),
                result.version().getVersion(), passingRun.getId());
        return new SubmitOutcome(result.skillId(), result.version().getId(), result.slug(),
                result.version().getVersion(), result);
    }

    private void assertOwner(SkillDraft draft, String userId, Set<String> platformRoles) {
        if (draft.getOwnerId().equals(userId)
                || (platformRoles != null && platformRoles.contains(SUPER_ADMIN))) {
            return;
        }
        throw new com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException(
                "error.authoring.draft.forbidden", draft.getId());
    }
}
