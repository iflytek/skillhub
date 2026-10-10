package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.review.PromotionRevocationRequest;
import java.time.Instant;

public record PromotionRevocationResponse(
        Long id, Long initialPromotionRequestId, Long sourceSkillId, Long targetSkillId,
        Long sourceNamespaceId, Long targetNamespaceId, String skillSlug,
        String status, String reason, String submittedBy, String reviewedBy,
        String reviewComment, Instant submittedAt, Instant reviewedAt) {
    public static PromotionRevocationResponse from(PromotionRevocationRequest request) {
        return new PromotionRevocationResponse(request.getId(), request.getInitialPromotionRequestId(),
                request.getSourceSkillId(), request.getTargetSkillId(), request.getSourceNamespaceId(),
                request.getTargetNamespaceId(), request.getSkillSlug(), request.getStatus().name(),
                request.getReason(), request.getSubmittedBy(), request.getReviewedBy(),
                request.getReviewComment(), request.getSubmittedAt(), request.getReviewedAt());
    }
}
