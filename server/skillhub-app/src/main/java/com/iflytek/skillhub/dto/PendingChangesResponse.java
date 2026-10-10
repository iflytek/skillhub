package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Most recent profile change request status for the current user.
 * Null when no active (PENDING or recently REJECTED) request exists.
 *
 * @param status        request status: PENDING or REJECTED
 * @param changes       map of field name → requested new value
 * @param reviewComment rejection reason (null when PENDING or approved)
 * @param createdAt     when the change request was submitted
 */
public record PendingChangesResponse(
        String status,
        Map<String, String> changes,
        String reviewComment,
        Instant createdAt
) {}
