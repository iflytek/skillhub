package com.iflytek.skillhub.dto;

/**
 * Current review-status totals for the author's grouped skill-version progress.
 */
public record ReviewProgressStatusCounts(
        long pending,
        long approved,
        long rejected
) {}
