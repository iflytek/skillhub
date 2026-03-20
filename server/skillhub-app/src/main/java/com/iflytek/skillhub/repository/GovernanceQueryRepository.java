package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.report.SkillReport;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.dto.GovernanceInboxItemResponse;
import com.iflytek.skillhub.dto.PromotionResponseDto;
import com.iflytek.skillhub.dto.ReviewTaskResponse;
import java.util.List;

/**
 * Query-side repository for governance read models assembled from review,
 * promotion, skill, namespace, and user sources.
 */
public interface GovernanceQueryRepository {
    ReviewTaskResponse getReviewTaskResponse(ReviewTask task);

    List<ReviewTaskResponse> getReviewTaskResponses(List<ReviewTask> tasks);

    PromotionResponseDto getPromotionResponse(PromotionRequest request);

    List<PromotionResponseDto> getPromotionResponses(List<PromotionRequest> requests);

    GovernanceInboxItemResponse getReviewInboxItem(ReviewTask task);

    List<GovernanceInboxItemResponse> getReviewInboxItems(List<ReviewTask> tasks);

    GovernanceInboxItemResponse getPromotionInboxItem(PromotionRequest request);

    List<GovernanceInboxItemResponse> getPromotionInboxItems(List<PromotionRequest> requests);

    GovernanceInboxItemResponse getReportInboxItem(SkillReport report);

    List<GovernanceInboxItemResponse> getReportInboxItems(List<SkillReport> reports);
}
