package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.authoring.SkillDraft;
import com.iflytek.skillhub.domain.authoring.SkillDraftRepository;
import com.iflytek.skillhub.domain.authoring.validation.FindingStatus;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFinding;
import com.iflytek.skillhub.domain.authoring.validation.ValidationFindingRepository;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRun;
import com.iflytek.skillhub.domain.authoring.validation.ValidationRunRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the fix loop for validation findings: an author previews a finding's
 * machine-applicable suggestion, then confirms it. Applying the patch produces a new
 * draft revision through {@link SkillDraftService#applyPatch} (which re-validates all
 * sha256 guards), marks the finding APPLIED, and thereby invalidates the previous
 * validation verdict — re-validation is always required afterwards.
 */
@Service
public class FindingFixService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final ValidationFindingRepository findingRepository;
    private final ValidationRunRepository runRepository;
    private final SkillDraftRepository draftRepository;
    private final SkillDraftService draftService;

    public FindingFixService(ValidationFindingRepository findingRepository,
                             ValidationRunRepository runRepository,
                             SkillDraftRepository draftRepository,
                             SkillDraftService draftService) {
        this.findingRepository = findingRepository;
        this.runRepository = runRepository;
        this.draftRepository = draftRepository;
        this.draftService = draftService;
    }

    public record FixOutcome(ValidationFinding finding, SkillDraft draft) {}

    /**
     * Applies the finding's fix suggestion to the draft. The whole operation is atomic:
     * either every patch lands and the finding is marked APPLIED, or nothing changes.
     */
    @Transactional
    public FixOutcome applyFix(Long findingId, String userId, Set<String> platformRoles) {
        OwnedFinding owned = loadOwnedFinding(findingId, userId, platformRoles);
        ValidationFinding finding = owned.finding();
        if (!finding.hasSuggestion()) {
            throw new DomainBadRequestException("error.authoring.finding.noSuggestion", findingId);
        }
        if (finding.getStatus() == FindingStatus.APPLIED) {
            throw new DomainConflictException("error.authoring.finding.applied", findingId);
        }

        SkillDraft draft = draftService.applyPatch(
                owned.draftId(), userId, finding.getSuggestion().safePatches(), null, platformRoles);
        finding.markApplied(draft.getRevision());
        findingRepository.save(finding);
        return new FixOutcome(finding, draft);
    }

    /** Dismisses a finding the author considers not applicable. */
    @Transactional
    public ValidationFinding dismissFinding(Long findingId, String userId, Set<String> platformRoles) {
        ValidationFinding finding = loadOwnedFinding(findingId, userId, platformRoles).finding();
        finding.markDismissed();
        return findingRepository.save(finding);
    }

    private record OwnedFinding(ValidationFinding finding, Long draftId) {}

    private OwnedFinding loadOwnedFinding(Long findingId, String userId, Set<String> platformRoles) {
        ValidationFinding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new DomainNotFoundException("error.authoring.finding.notFound", findingId));
        ValidationRun run = runRepository.findById(finding.getRunId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.authoring.run.notFound", finding.getRunId()));
        SkillDraft draft = draftRepository.findById(run.getDraftId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.authoring.draft.notFound", run.getDraftId()));
        if (draft.getOwnerId().equals(userId)
                || (platformRoles != null && platformRoles.contains(SUPER_ADMIN))) {
            return new OwnedFinding(finding, draft.getId());
        }
        throw new DomainForbiddenException("error.authoring.draft.forbidden", draft.getId());
    }
}
