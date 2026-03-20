package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.dto.ProfileReviewSummaryResponse;
import java.util.List;

/**
 * Query-side repository for admin-facing profile review summaries.
 */
public interface ProfileReviewQueryRepository {
    List<ProfileReviewSummaryResponse> getProfileReviewSummaries(List<ProfileChangeRequest> requests);
}
