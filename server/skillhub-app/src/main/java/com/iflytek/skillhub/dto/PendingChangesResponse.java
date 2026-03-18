package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Pending profile changes awaiting human review.
 * Null when no PENDING request exists for the user.
 *
 * @param status    always "PENDING" when present
 * @param changes   map of field name → requested new value
 * @param createdAt when the change request was submitted
 */
public record PendingChangesResponse(
        String status,
        Map<String, String> changes,
        Instant createdAt
) {}
