package com.iflytek.skillhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.ProfileReviewService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ProfileReviewSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service bridging the admin controller and domain layer
 * for profile change request reviews.
 *
 * <p>Handles DTO mapping and user info resolution (userId → username/displayName).
 */
@Service
public class AdminProfileReviewAppService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final ProfileReviewService profileReviewService;
    private final UserAccountRepository userAccountRepository;

    public AdminProfileReviewAppService(ProfileReviewService profileReviewService,
                                        UserAccountRepository userAccountRepository) {
        this.profileReviewService = profileReviewService;
        this.userAccountRepository = userAccountRepository;
    }

    /** List profile change requests by status with user info resolution. */
    @Transactional(readOnly = true)
    public PageResponse<ProfileReviewSummaryResponse> list(String status, int page, int size) {
        var resolvedStatus = parseStatus(status);
        var requestPage = profileReviewService.listByStatus(resolvedStatus, PageRequest.of(page, size));

        // Batch-load user accounts for submitters and reviewers
        var allUserIds = requestPage.getContent().stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getUserId(), r.getReviewerId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        var usersById = allUserIds.isEmpty()
                ? Map.<String, UserAccount>of()
                : userAccountRepository.findByIdIn(allUserIds).stream()
                        .collect(Collectors.toMap(UserAccount::getId, Function.identity()));

        var items = requestPage.getContent().stream()
                .map(req -> toResponse(req, usersById))
                .toList();

        return new PageResponse<>(items, requestPage.getTotalElements(),
                requestPage.getNumber(), requestPage.getSize());
    }

    // ── Private helpers ──────────────────────────────────────────────

    private ProfileReviewSummaryResponse toResponse(ProfileChangeRequest req,
                                                     Map<String, UserAccount> usersById) {
        var submitter = usersById.get(req.getUserId());
        var reviewer = req.getReviewerId() != null ? usersById.get(req.getReviewerId()) : null;
        var changes = parseChangesJson(req.getChanges());
        var oldValues = parseChangesJson(req.getOldValues());
        var currentDisplayName = oldValues.getOrDefault(
                "displayName",
                submitter != null ? submitter.getDisplayName() : null
        );

        return new ProfileReviewSummaryResponse(
                req.getId(),
                req.getUserId(),
                submitter != null ? submitter.getDisplayName() : req.getUserId(),
                currentDisplayName,
                changes.getOrDefault("displayName", null),
                req.getStatus().name(),
                req.getMachineResult(),
                req.getReviewerId(),
                reviewer != null ? reviewer.getDisplayName() : null,
                req.getReviewComment(),
                req.getCreatedAt(),
                req.getReviewedAt()
        );
    }

    private ProfileChangeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return ProfileChangeStatus.PENDING;
        }
        try {
            return ProfileChangeStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.profileReview.status.invalid", status);
        }
    }

    private Map<String, String> parseChangesJson(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
