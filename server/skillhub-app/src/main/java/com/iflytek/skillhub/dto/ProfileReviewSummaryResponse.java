package com.iflytek.skillhub.dto;

import java.time.Instant;

/**
 * Summary DTO for a profile change request in the admin review list.
 *
 * @param id                  change request ID
 * @param userId              user who submitted the change
 * @param username            user's login name (for display)
 * @param currentDisplayName  display name snapshot before this request was submitted
 * @param requestedDisplayName requested new display name
 * @param status              request status (PENDING, APPROVED, REJECTED, etc.)
 * @param machineResult       machine review result (PASS, FAIL, SKIPPED, or null)
 * @param reviewerId          admin who reviewed (null if pending)
 * @param reviewerName        reviewer display name (null if pending)
 * @param reviewComment       review comment (null if pending or approved without comment)
 * @param createdAt           when the change was submitted
 * @param reviewedAt          when the review was performed (null if pending)
 */
public record ProfileReviewSummaryResponse(
        Long id,
        String userId,
        String username,
        String currentDisplayName,
        String requestedDisplayName,
        String status,
        String machineResult,
        String reviewerId,
        String reviewerName,
        String reviewComment,
        Instant createdAt,
        Instant reviewedAt
) {}
