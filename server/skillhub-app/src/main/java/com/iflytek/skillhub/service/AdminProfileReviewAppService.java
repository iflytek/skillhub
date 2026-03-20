package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.ProfileReviewService;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ProfileReviewSummaryResponse;
import com.iflytek.skillhub.repository.ProfileReviewQueryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service bridging the admin controller and domain layer
 * for profile change request reviews.
 *
 * <p>Handles DTO mapping and user info resolution (userId → username/displayName).
 */
@Service
public class AdminProfileReviewAppService {

    private final ProfileReviewService profileReviewService;
    private final ProfileReviewQueryRepository profileReviewQueryRepository;

    public AdminProfileReviewAppService(ProfileReviewService profileReviewService,
                                        ProfileReviewQueryRepository profileReviewQueryRepository) {
        this.profileReviewService = profileReviewService;
        this.profileReviewQueryRepository = profileReviewQueryRepository;
    }

    /** List profile change requests by status with user info resolution. */
    @Transactional(readOnly = true)
    public PageResponse<ProfileReviewSummaryResponse> list(String status, int page, int size) {
        var resolvedStatus = parseStatus(status);
        var requestPage = profileReviewService.listByStatus(resolvedStatus, PageRequest.of(page, size));
        var items = profileReviewQueryRepository.getProfileReviewSummaries(requestPage.getContent());

        return new PageResponse<>(items, requestPage.getTotalElements(),
                requestPage.getNumber(), requestPage.getSize());
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
}
