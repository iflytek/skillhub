package com.iflytek.skillhub.dto;

import java.time.Instant;

/** An immutable acquisition group, counted before pagination. */
public record GithubReviewGroupResponse(long snapshotId, long namespaceId, String namespace,
        long repositoryId, String owner, String repository, String commitSha, Instant capturedAt,
        long total, long scanPassed, long scanWaiting, long scanBlocked) {}
